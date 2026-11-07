package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.time.Clock;
import java.time.Instant;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementPeriod;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowCounts;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowOutcome;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.RevisionData;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.PeriodRules;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeScope;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementRepository;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorDirectory;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFailure;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularSheet;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Trabajo en segundo plano de un lote. Cada fila registra su resultado en la transaccion que escribe
 * la medicion: reanudar continua donde quedo. Al confirmar se vuelve a comparar con lo registrado,
 * porque la decision de la persona vale para lo que vio en la vista previa.
 */
@Service
class UptimeImportProcessor {

	private static final Logger log = LoggerFactory.getLogger(UptimeImportProcessor.class);
	static final int CHUNK_SIZE = 500;

	private final UptimeRowParser parser;
	private final ConnectorResolver resolver;
	private final ConnectorDirectory connectors;
	private final ImportedMeasurements imported;
	private final UptimeImportRepository imports;
	private final MeasurementRepository measurements;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final OperatingZone zone;
	private final Clock clock;

	UptimeImportProcessor(UptimeRowParser parser, ConnectorResolver resolver, ConnectorDirectory connectors,
			ImportedMeasurements imported, UptimeImportRepository imports, MeasurementRepository measurements,
			AuditTrail audit, TransactionTemplate transactions, OperatingZone zone, Clock clock) {
		this.parser = parser;
		this.resolver = resolver;
		this.connectors = connectors;
		this.imported = imported;
		this.imports = imports;
		this.measurements = measurements;
		this.audit = audit;
		this.transactions = transactions;
		this.zone = zone;
		this.clock = clock;
	}

	void prepare(UUID batchId, Supplier<List<TabularSheet>> original) {
		try {
			List<TabularSheet> tabular = original.get();
			transactions.executeWithoutResult(status -> classify(batchId, tabular));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	void apply(UUID batchId) {
		try {
			while (applyChunk(batchId)) {
				// Cada vuelta confirma un tramo; se repite hasta que no quedan filas sin resultado.
			}
			transactions.executeWithoutResult(status -> complete(batchId));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

    private boolean applyChunk(UUID batchId) {
        try { return Boolean.TRUE.equals(transactions.execute(status->applyNextChunk(batchId))); }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException concurrent) {
            // La transaccion abortada no se reutiliza. Releer reconoce la identidad ganadora.
            return Boolean.TRUE.equals(transactions.execute(status->applyNextChunk(batchId)));
        }
    }

	private void classify(UUID batchId, List<TabularSheet> tabular) {
		UptimeImportBatch batch = load(batchId);
		UptimeRowParser.DeclaredPeriod period = batch.getReferenceWeek() == null ? null
                : new UptimeRowParser.DeclaredPeriod(batch.getReferenceWeek());
		List<UptimeRowParser.ParsedRow> parsed = parser.parse(tabular, period);
		if (parsed.isEmpty()) {
			throw new BusinessRuleException("IMPORT_NO_ROWS", "El archivo no tiene filas de uptime.");
		}
		Map<ConnectorResolver.Request, ConnectorResolver.Resolution> resolutions = resolver.resolve(batch.getSource(),
				parsed.stream().filter(UptimeRowParser.ParsedRow::isValid).map(UptimeImportProcessor::request).distinct()
						.toList());
		Instant now = clock.instant();
		Set<String> seen = new HashSet<>();
		List<UptimeImportRow> rows = parsed.stream().map(row -> classifyRow(batch, row, resolutions, seen, now)).toList();
		imports.insertRows(rows);
		UptimeRowCounts counts = UptimeRowCounts.of(rows);
		batch.previewReady(counts);
		imports.saveBatch(batch);
		audit.record("UPTIME_IMPORT_PREVIEW_READY", UptimeImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("rows", String.valueOf(counts.total()), "new", String.valueOf(counts.newRows()), "conflict",
						String.valueOf(counts.conflict()), "invalid", String.valueOf(counts.invalid()), "pendingLink",
						String.valueOf(counts.pendingLink())));
	}

	private UptimeImportRow classifyRow(UptimeImportBatch batch, UptimeRowParser.ParsedRow parsed,
			Map<ConnectorResolver.Request, ConnectorResolver.Resolution> resolutions, Set<String> seen, Instant now) {
		UptimeImportRow row = UptimeImportRow.read(batch.getId(), parsed.rowNumber(), parsed.sheet(), parsed.sheetRow(),
				parsed.site(), parsed.charger(), parsed.connector(), parsed.periodStart(), parsed.periodEnd(),
				parsed.sourceValues());
		if (!parsed.isValid()) {
			return invalid(row, parsed.issues());
		}
		PeriodType declared = batch.getDeclaredPeriodType();
		try {
			MeasurementPeriod period = parsed.periodStart()==null && parsed.periodEnd()==null
                ? MeasurementPeriod.reference(batch.getReferenceWeek(),zone.zone().getId())
                : MeasurementPeriod.known(parsed.periodStart(),parsed.periodEnd(),zone.zone().getId(),batch.getReferenceWeek());
            period.validate(declared,false,now);
            row.usePeriod(period);
            row.assess(declared,MeasurementRules.assess(parsed.values(),period,declared),parsed.values().reportedPercent());
		}
		catch (BusinessRuleException ex) {
			return invalid(row, List.of(new RowIssue(ex.code(), ex.getMessage())));
		}
		ConnectorResolver.Resolution resolution = resolutions.get(request(parsed));
		if (resolution.equipment() != null
				&& !UptimeScope.includes(resolution.equipment().network(), resolution.equipment().type())) {
			return invalid(row, List.of(new RowIssue("UPTIME_OUT_OF_SCOPE", "Esta infraestructura no se evalua en uptime.")));
		}
		MeasurableConnector connector = resolution.connector();
		if (connector == null || connector.equipmentStatus() == AdministrativeStatus.DELETED) {
			UUID equipmentId = resolution.equipment() == null ? null : resolution.equipment().id();
			row.classify(UptimeRowClassification.PENDING_LINK, equipmentId, null, null, null,
					List.of(resolution.issue() == null
							? new RowIssue("UPTIME_CONNECTOR_NOT_MEASURABLE", "El conector no se puede medir.")
							: resolution.issue()));
			if (resolution.suggested() != null) {
				row.suggest(resolution.suggested().connectorId());
			}
			return row;
		}
		if (!seen.add(connector.connectorId() + "|" + batch.getSource() + "|" + row.getPeriod(zone.zone().getId()))) {
			return invalid(row,
					List.of(new RowIssue("DUPLICATED_IN_BATCH", "El conector y período ya aparecen antes en este lote.")));
		}
		compare(batch, row, connector);
		return row;
	}

	private void compare(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector) {
		Optional<Measurement> existing = measurements.findByIdentity(connector.connectorId(), batch.getSource(),
				row.getPeriod(zone.zone().getId()));
		if (existing.isEmpty()) {
			row.classify(UptimeRowClassification.NEW, connector.equipmentId(), connector.connectorId(), null, null,
					List.of());
			return;
		}
		Measurement measurement = existing.get();
		MeasurementRevision latest = latest(measurement);
		boolean same = latest.sameReading(comparable(batch, row, connector, latest));
		row.classify(same ? UptimeRowClassification.IDENTICAL : UptimeRowClassification.CONFLICT,
				connector.equipmentId(), connector.connectorId(), measurement.getId(), latest.getRevision(),
				same ? List.of() : List.of(new RowIssue("UPTIME_MEASUREMENT_DIFFERS",
						"Ya existe una medición distinta para ese conector y período.")));
	}

	/** Una semana ya confirmada exacta no es otra lectura por volver a llegar como ventana movil. */
	private RevisionData comparable(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector,
			MeasurementRevision latest) {
		boolean exact = latest.getPeriodType().isExact() && imported.qualifiesAsExact(row);
		return imported.data(batch, row, connector, exact ? PeriodType.EXACT_WEEK : row.getPeriodType(), false, null,
				null);
	}

	private static UptimeImportRow invalid(UptimeImportRow row, List<RowIssue> issues) {
		row.classify(UptimeRowClassification.INVALID, null, null, null, null, new ArrayList<>(issues));
		return row;
	}

	private boolean applyNextChunk(UUID batchId) {
		UptimeImportBatch batch = load(batchId);
		List<UptimeImportRow> rows = imports.nextToApply(batchId, CHUNK_SIZE);
		if (rows.isEmpty()) {
			return false;
		}
		Map<UUID, MeasurableConnector> found = connectors.findByIds(rows.stream().map(UptimeImportRow::getConnectorId)
				.filter(java.util.Objects::nonNull).distinct().toList());
		rows.forEach(row -> applyRow(batch, row, row.getConnectorId() == null ? null : found.get(row.getConnectorId())));
		return true;
	}

	private void applyRow(UptimeImportBatch batch, UptimeImportRow row, MeasurableConnector connector) {
		if (row.getClassification() == UptimeRowClassification.PENDING_LINK) {
			row.recordOutcome(UptimeRowOutcome.PENDING_LINK, null);
			return;
		}
		boolean writes = row.getClassification() == UptimeRowClassification.NEW
				|| row.getClassification() == UptimeRowClassification.CONFLICT;
		if (!writes || !row.isApproved()) {
			row.recordOutcome(UptimeRowOutcome.SKIPPED, null);
			return;
		}
		if (connector == null || connector.equipmentStatus() == AdministrativeStatus.DELETED) {
			row.reject(new RowIssue("UPTIME_CONNECTOR_NOT_MEASURABLE", "El conector ya no se puede medir."));
			return;
		}
		RevisionData data = imported.data(batch, row, connector, batch.getConfirmedBy(), row.getJustification());
		Optional<Measurement> existing = measurements.findByIdentity(connector.connectorId(), batch.getSource(),
				row.getPeriod(zone.zone().getId()));
		if (existing.isEmpty()) {
			row.recordOutcome(UptimeRowOutcome.APPLIED, imported.create(batch, row, connector, data).getId());
			return;
		}
		Measurement measurement = existing.get();
		MeasurementRevision latest = latest(measurement);
		if (latest.sameReading(data)) {
			row.recordOutcome(UptimeRowOutcome.SKIPPED, measurement.getId());
		}
		else if (row.getClassification() == UptimeRowClassification.CONFLICT
				&& Integer.valueOf(latest.getRevision()).equals(row.getExistingRevision())) {
			imported.correct(batch, measurement, latest, data);
			row.recordOutcome(UptimeRowOutcome.APPLIED, measurement.getId());
		}
		else {
			row.reject(new RowIssue("UPTIME_ROW_RECLASSIFIED",
					"La medición cambió después de la vista previa; revísala antes de volver a importar."));
		}
	}

	private void complete(UUID batchId) {
		UptimeImportBatch batch = load(batchId);
		int written = imports.countApplied(batchId);
		batch.complete(written);
		imports.saveBatch(batch);
		audit.record("UPTIME_IMPORT_COMPLETED", UptimeImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("written", String.valueOf(written)));
	}

	private void fail(UUID batchId, RuntimeException ex) {
		ImportFailure failure = ImportFailure.of(ex);
		if (failure.unexpected()) {
			log.error("Fallo el procesamiento del lote de uptime {}", batchId, ex);
		}
		transactions.executeWithoutResult(status -> {
			UptimeImportBatch batch = load(batchId);
			if (batch.isInProgress()) {
				batch.fail(failure.code(), failure.message());
				imports.saveBatch(batch);
				audit.record("UPTIME_IMPORT_FAILED", UptimeImportBatch.AUDIT_ENTITY_TYPE, batchId,
						Map.of("code", failure.code()));
			}
		});
	}

	private MeasurementRevision latest(Measurement measurement) {
		return measurements.findRevision(measurement.getId(), measurement.getLatestRevision()).orElseThrow();
	}

	private UptimeImportBatch load(UUID id) {
		return imports.findBatch(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}

	private static ConnectorResolver.Request request(UptimeRowParser.ParsedRow row) {
		return new ConnectorResolver.Request(row.site(), row.charger(), row.connector());
	}
}
