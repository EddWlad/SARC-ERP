package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.application.port.ParsedSession;
import com.tidsec.sarc_bakend.modules.consumption.application.port.SessionReportParser;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowCounts;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.PreferredSourcePolicy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportBatchRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportRowRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFailure;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Trabajo en segundo plano de un lote (TRD 09.1): interpretar y clasificar para la vista previa, y
 * luego incorporar las filas aprobadas en tramos transaccionales. Cada fila registra su resultado en
 * la misma transaccion que escribe la sesion; por eso reanudar un lote interrumpido continua donde
 * quedo y nunca duplica sesiones (ademas de la clave unica por fuente).
 */
@Service
class ImportProcessor {

	private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);
	static final int CHUNK_SIZE = 500;

	private final Map<SessionSource, SessionReportParser> parsers;
	private final EquipmentResolver resolver;
	private final ImportBatchRepository batches;
	private final ImportRowRepository rows;
	private final EnergySessionRepository sessions;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final OperatingZone zone;
	private final CurrentActor currentActor;
	private final Clock clock;

	ImportProcessor(List<SessionReportParser> parsers, EquipmentResolver resolver, ImportBatchRepository batches,
			ImportRowRepository rows, EnergySessionRepository sessions, AuditTrail audit,
			TransactionTemplate transactions, OperatingZone zone, CurrentActor currentActor, Clock clock) {
		this.parsers = new EnumMap<>(parsers.stream()
				.collect(Collectors.toMap(SessionReportParser::source, Function.identity())));
		this.resolver = resolver;
		this.batches = batches;
		this.rows = rows;
		this.sessions = sessions;
		this.audit = audit;
		this.transactions = transactions;
		this.zone = zone;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Interpreta el original y deja la vista previa lista; si no se puede leer, el lote queda fallido. */
	void prepare(UUID batchId, Supplier<List<TabularRow>> original) {
		try {
			List<TabularRow> tabular = original.get();
			transactions.executeWithoutResult(status -> classify(batchId, tabular));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	/** Incorpora las filas aplicables por tramos y cierra el lote. */
	void apply(UUID batchId) {
		try {
			while (Boolean.TRUE.equals(transactions.execute(status -> applyNextChunk(batchId)))) {
				// Cada vuelta confirma un tramo; se repite hasta que no quedan filas por incorporar.
			}
			transactions.executeWithoutResult(status -> complete(batchId));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	private void classify(UUID batchId, List<TabularRow> tabular) {
		ImportBatch batch = load(batchId);
		List<ParsedSession> parsed = parser(batch.getSource()).parse(tabular);
		if (parsed.isEmpty()) {
			throw new BusinessRuleException("IMPORT_NO_ROWS", "El archivo no tiene filas de sesiones");
		}
		List<ParsedSession> valid = parsed.stream().filter(ParsedSession::isValid).toList();
		Map<EquipmentLookup, EquipmentReference> equipment = resolver.resolve(batch.getSource(),
				valid.stream().map(ParsedSession::lookup).distinct().toList());
		Map<String, EnergySession> existing = sessions.findByKeys(batch.getSource(), batch.getSourceScope(),
				valid.stream().map(ParsedSession::externalSessionId).distinct().toList()).stream()
				.collect(Collectors.toMap(EnergySession::getExternalSessionId, Function.identity()));

		Set<String> seen = new HashSet<>();
		List<ImportRow> classified = parsed.stream()
				.map(row -> classifyRow(batch, row, equipment, existing, seen)).toList();
		rows.insertAll(classified);
		RowCounts counts = RowCounts.of(classified.stream().map(ImportRow::getClassification).toList());
		batch.previewReady(counts);
		batches.save(batch);
		audit.record("ENERGY_IMPORT_PREVIEW_READY", ImportBatch.AUDIT_ENTITY_TYPE, batchId, Map.of(
				"rows", String.valueOf(counts.total()),
				"new", String.valueOf(counts.newRows()),
				"modified", String.valueOf(counts.modified()),
				"invalid", String.valueOf(counts.invalid()),
				"pendingLink", String.valueOf(counts.pendingLink())));
	}

	private ImportRow classifyRow(ImportBatch batch, ParsedSession parsed,
			Map<EquipmentLookup, EquipmentReference> equipment, Map<String, EnergySession> existing, Set<String> seen) {
		if (!parsed.isValid()) {
			return ImportRow.invalid(batch.getId(), parsed.rowNumber(), parsed.externalSessionId(), parsed.issues());
		}
		String id = parsed.externalSessionId();
		EquipmentReference found = equipment.get(parsed.lookup());
		List<RowIssue> issues = new ArrayList<>();
		if (!seen.add(id)) {
			issues.add(new RowIssue("DUPLICATED_IN_BATCH", "La sesion " + id + " ya aparece antes en este lote"));
			return ImportRow.classified(batch.getId(), parsed.rowNumber(), id, parsed.reading(), found,
					RowClassification.DUPLICATE, false, null, null, issues);
		}
		EnergySession current = existing.get(id);
		RowClassification classification;
		if (current != null && !current.identifiesSameCharger(parsed.reading())) {
			classification = RowClassification.EQUIPMENT_CHANGED;
			issues.add(new RowIssue("SESSION_EQUIPMENT_CHANGED", "La sesion estaba en "
					+ chargerOf(batch.getSource(), current.reading()) + " y ahora llega en "
					+ chargerOf(batch.getSource(), parsed.reading())
					+ (found == null ? ", que no esta identificado en el inventario" : "")
					+ ". Reasignela solo si es la misma carga mal registrada; aprobar no basta"));
		}
		else if (current != null) {
			classification = current.hasSameReading(parsed.reading()) ? RowClassification.IDENTICAL
					: RowClassification.MODIFIED;
			if (classification == RowClassification.MODIFIED) {
				issues.add(new RowIssue("SESSION_CHANGED",
						"Los datos difieren de los ya importados; apruebe para corregir (se conserva la version anterior)"));
			}
		}
		else if (found == null) {
			classification = RowClassification.PENDING_LINK;
			issues.add(new RowIssue("EQUIPMENT_NOT_IDENTIFIED", unidentified(batch.getSource(), parsed.lookup())));
		}
		else {
			classification = RowClassification.NEW;
		}
		boolean nonPreferred = classification.writesSession() && found != null
				&& !PreferredSourcePolicy.isPreferred(batch.getSource(), found.network(), found.type());
		if (nonPreferred) {
			issues.add(new RowIssue("SOURCE_NOT_PREFERRED", "La fuente preferente de este equipo es "
					+ PreferredSourcePolicy.preferredFor(found.network(), found.type())
					+ "; apruebe solo si esta sesion no la reporta tambien esa plataforma"));
		}
		return ImportRow.classified(batch.getId(), parsed.rowNumber(), id, parsed.reading(), found, classification,
				nonPreferred, current == null ? null : current.getId(), current == null ? null : current.getRevision(),
				issues);
	}

	/** Como identifica la fuente al cargador: serie en Wallbox, ACTIVO / CARGADOR en Enerlink. */
	private static String chargerOf(SessionSource source, SessionReading reading) {
		return switch (source) {
			case WALLBOX -> "la serie " + Objects.requireNonNullElse(reading.serial(), "(vacia)");
			case ENERLINK -> Objects.requireNonNullElse(reading.site(), "(sin activo)") + " / "
					+ Objects.requireNonNullElse(reading.charger(), "(sin cargador)");
		};
	}

	private static String unidentified(SessionSource source, EquipmentLookup lookup) {
		return switch (lookup) {
			case EquipmentLookup.BySerial serial -> "No hay en el inventario un equipo con la serie " + serial.serial()
					+ " ni una identidad " + source + " verificada con ese valor";
			case EquipmentLookup.ByIdentity identity -> "Registre ACTIVO \"" + identity.key().scope()
					+ "\" y CARGADOR \"" + identity.key().externalId() + "\" como identidad " + source
					+ " verificada del equipo; quedara pendiente de vincular";
		};
	}

	private boolean applyNextChunk(UUID batchId) {
		ImportBatch batch = load(batchId);
		List<ImportRow> next = rows.nextToApply(batchId, CHUNK_SIZE);
		if (next.isEmpty()) {
			return false;
		}
		Map<String, EnergySession> current = sessions.findByKeys(batch.getSource(), batch.getSourceScope(),
				next.stream().map(ImportRow::getExternalSessionId).toList()).stream()
				.collect(Collectors.toMap(EnergySession::getExternalSessionId, Function.identity()));
		UUID actorId = currentActor.id().orElse(null);
		for (ImportRow row : next) {
			Outcome outcome = applyRow(batch, row, current.get(row.getExternalSessionId()), actorId);
			row.recordOutcome(outcome.result(), outcome.detail());
		}
		return true;
	}

	private record Outcome(RowOutcome result, String detail) {

		static Outcome of(RowOutcome result) {
			return new Outcome(result, null);
		}

		static Outcome skipped(String detail) {
			return new Outcome(RowOutcome.SKIPPED, detail);
		}
	}

	private Outcome applyRow(ImportBatch batch, ImportRow row, EnergySession session, UUID actorId) {
		if (session == null) {
			sessions.save(EnergySession.record(batch.getSource(), batch.getSourceScope(), row.getExternalSessionId(),
					row.reading(), row.equipment(), row.isApproved(), row.isTest(), batch.getId(), zone));
			return Outcome.of(RowOutcome.INSERTED);
		}
		if (row.getExistingSessionRevision() != null && row.getExistingSessionRevision() != session.getRevision()) {
			// La decision se tomo sobre otra version: aplicarla seria corregir algo que nadie reviso.
			return Outcome.skipped("La sesion cambio despues de la vista previa; vuelva a cargar el archivo para revisarla");
		}
		if (session.hasSameReading(row.reading())) {
			return Outcome.of(RowOutcome.UNCHANGED);
		}
		if (row.getClassification() == RowClassification.EQUIPMENT_CHANGED) {
			sessions.saveRevision(session.reassign(row.reading(), row.equipment(), row.isApproved(), batch.getId(),
					zone, actorId, clock.instant()));
			if (row.isTest()) {
				session.markTest(true);
			}
			return Outcome.of(RowOutcome.REASSIGNED);
		}
		if (row.getClassification() != RowClassification.MODIFIED || !row.isApproved()) {
			// Otra importacion trajo la sesion despues de la vista previa: no se corrige sin aprobacion.
			return Outcome.skipped("La sesion ya existe con otros datos; no se corrige sin una aprobacion explicita");
		}
		if (!session.identifiesSameCharger(row.reading())) {
			return Outcome.skipped("La correccion cambia el cargador de la sesion; debe reasignarse");
		}
		sessions.saveRevision(session.correct(row.reading(), batch.getId(), zone, actorId, clock.instant()));
		if (row.isTest()) {
			session.markTest(true);
		}
		return Outcome.of(RowOutcome.CORRECTED);
	}

	private void complete(UUID batchId) {
		ImportBatch batch = load(batchId);
		rows.markUnapprovedAsSkipped(batchId);
		int written = rows.countWritten(batchId);
		batch.complete(written);
		batches.save(batch);
		audit.record("ENERGY_IMPORT_COMPLETED", ImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("written", String.valueOf(written)));
	}

	private void fail(UUID batchId, RuntimeException ex) {
		ImportFailure failure = ImportFailure.of(ex);
		if (failure.unexpected()) {
			log.error("Fallo el procesamiento del lote {}", batchId, ex);
		}
		transactions.executeWithoutResult(status -> {
			ImportBatch batch = load(batchId);
			if (batch.isInProgress()) {
				batch.fail(failure.code(), failure.message());
				batches.save(batch);
				audit.record("ENERGY_IMPORT_FAILED", ImportBatch.AUDIT_ENTITY_TYPE, batchId,
						Map.of("code", failure.code()));
			}
		});
	}

	private SessionReportParser parser(SessionSource source) {
		SessionReportParser parser = parsers.get(source);
		if (parser == null) {
			throw new BusinessRuleException("IMPORT_SOURCE_NOT_SUPPORTED", "No hay un importador para " + source);
		}
		return parser;
	}

	private ImportBatch load(UUID batchId) {
		return batches.findById(batchId)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
