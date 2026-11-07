package com.tidsec.sarc_bakend.modules.uptime.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.uptime.application.command.PasteUptimeImportCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.command.UploadUptimeImportCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.command.UptimeRowDecisionCommand;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFiles;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularSheet;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Ciclo de vida de los lotes de uptime. El trabajo pesado corre en {@link UptimeImportProcessor}. */
@Service
@Transactional
@PreAuthorize(UptimePermissions.EDITORS)
public class UptimeImportService {

	private static final String STORAGE_FOLDER = "imports/uptime/";
	private static final String PASTE_FILE_NAME = "pegado.txt";

	private final UptimeImportRepository imports;
	private final UptimeImportProcessor processor;
	private final TabularReader reader;
	private final FileStorage fileStorage;
	private final ImportJobs jobs;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor actor;
	private final OperatingZone zone;
	private final Clock clock;

	UptimeImportService(UptimeImportRepository imports, UptimeImportProcessor processor, TabularReader reader,
			FileStorage fileStorage, ImportJobs jobs, AuditTrail audit, TransactionTemplate transactions,
			CurrentActor actor, OperatingZone zone, Clock clock) {
		this.imports = imports;
		this.processor = processor;
		this.reader = reader;
		this.fileStorage = fileStorage;
		this.jobs = jobs;
		this.audit = audit;
		this.transactions = transactions;
		this.actor = actor;
		this.zone = zone;
		this.clock = clock;
	}

	/**
	 * Informe semanal en XLSX, XLS o CSV, de lunes 00:00 a lunes siguiente en America/Guayaquil. Se leen
	 * todas sus hojas (D09, excepcion del informe semanal de uptime).
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public UptimeImportBatch upload(UploadUptimeImportCommand command) {
		LocalDate monday = command.weekStart();
		if (monday == null || monday.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessRuleException("UPTIME_WEEK_INVALID", "La semana del informe empieza un lunes.");
		}
		Instant start = monday.atStartOfDay(zone.zone()).toInstant();
		Instant end = monday.plusWeeks(1).atStartOfDay(zone.zone()).toInstant();
		if (end.isAfter(clock.instant())) {
			throw new BusinessRuleException("UPTIME_WEEK_NOT_CLOSED", "La semana del informe aún no termina.");
		}
		byte[] content = command.content();
		ImportFiles.requireContent(content.length);
		String fileName = ImportFiles.safeName(command.fileName(), "uptime");
		return open((id, reference) -> UptimeImportBatch.openWeeklyReference(id, MeasurementSource.ENERLINK, monday,
				fileName, ContentFingerprint.sha256(content), reference), fileName, content,
				Map.of("origin", ImportOrigin.FILE.name(), "weekStart", monday.toString()),
				() -> reader.readSheets(content));
	}

	/** Filas pegadas con su encabezado; la vista previa se prepara en segundo plano. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public UptimeImportBatch paste(PasteUptimeImportCommand command) {
		requireDeclaredWindow(command.declaredPeriodType());
		String text = command.text() == null ? "" : command.text();
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		ImportFiles.requireContent(text.isBlank() ? 0 : content.length);
		PeriodType declared = command.declaredPeriodType();
		return open((id, reference) -> UptimeImportBatch.open(id, MeasurementSource.ENERLINK, declared,
				ImportOrigin.PASTE, PASTE_FILE_NAME, ContentFingerprint.sha256(content), reference), PASTE_FILE_NAME,
				content, Map.of("origin", ImportOrigin.PASTE.name(), "declaredPeriodType", declared.name()),
				() -> List.of(new TabularSheet("", reader.readText(text))));
	}

	@Transactional(readOnly = true)
	public UptimeImportBatch get(UUID id) {
		return load(id);
	}

	@Transactional(readOnly = true)
	public PageResult<UptimeImportBatch> history(PageQuery page) {
		return imports.searchBatches(page);
	}

	@Transactional(readOnly = true)
	public PageResult<UptimeImportRow> rows(UUID batchId, UptimeRowClassification classification, PageQuery page) {
		load(batchId);
		return imports.searchRows(batchId, classification, page);
	}

	/** Incluir u omitir mientras la vista previa esta abierta. */
	public List<UptimeImportRow> decide(UptimeRowDecisionCommand command) {
		load(command.batchId()).requireReviewable();
		Set<UUID> requested = Set.copyOf(command.rowIds());
		List<UptimeImportRow> found = imports.findRows(command.batchId(), requested);
		if (found.size() != requested.size()) {
			throw new NotFoundException("IMPORT_ROW_NOT_FOUND", "Alguna fila no pertenece a este lote");
		}
		found.forEach(row -> row.decide(command.approved(), command.justification()));
		return found;
	}

	public UptimeImportBatch confirm(UUID id, long version, boolean exactConfirmed) {
		UptimeImportBatch batch = loadForChange(id, version);
        if (exactConfirmed && imports.hasRowsWithoutKnownRange(id))
            throw new BusinessRuleException("UPTIME_REAL_RANGE_REQUIRED",
                "El informe sin horario real no se puede confirmar como semana exacta.");
		UUID author = actor.id().orElse(null);
		Instant now = clock.instant();
		batch.confirm(author, now);
		if (exactConfirmed) {
			batch.confirmExact(author, now);
		}
		imports.saveBatch(batch);
		audit.record("UPTIME_IMPORT_CONFIRMED", UptimeImportBatch.AUDIT_ENTITY_TYPE, id,
				Map.of("exactConfirmed", String.valueOf(exactConfirmed)));
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	/** Continua con las filas que aun no tienen resultado. */
	public UptimeImportBatch retry(UUID id, long version) {
		UptimeImportBatch batch = loadForChange(id, version);
		batch.retry();
		imports.saveBatch(batch);
		audit.record("UPTIME_IMPORT_RETRIED", UptimeImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	public UptimeImportBatch discard(UUID id, long version) {
		UptimeImportBatch batch = loadForChange(id, version);
		batch.discard();
		imports.saveBatch(batch);
		audit.record("UPTIME_IMPORT_DISCARDED", UptimeImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		return batch;
	}

	private static void requireDeclaredWindow(PeriodType declared) {
		if (declared == PeriodType.EXACT_WEEK) {
			throw new BusinessRuleException("UPTIME_DECLARED_TYPE_INVALID",
					"Declara la ventana consultada; la semana exacta se confirma al final.");
		}
	}

	/** El original se guarda antes de abrir la transaccion: no retiene una conexion durante la subida. */
	private UptimeImportBatch open(BiFunction<UUID, String, UptimeImportBatch> opening, String fileName,
			byte[] content, Map<String, String> details, Supplier<List<TabularSheet>> original) {
		UUID id = UUID.randomUUID();
		String reference = fileStorage.storeDocument(STORAGE_FOLDER + id, fileName, content);
		return transactions.execute(status -> {
			UptimeImportBatch batch = imports.saveBatch(opening.apply(id, reference));
			Map<String, String> audited = new HashMap<>(details);
			audited.put("sha256", batch.getChecksum());
			audit.record("UPTIME_IMPORT_CREATED", UptimeImportBatch.AUDIT_ENTITY_TYPE, id, audited);
			jobs.submitAfterCommit(() -> processor.prepare(id, original), failWhenBusy(id));
			return batch;
		});
	}

	private UptimeImportBatch loadForChange(UUID id, long version) {
		UptimeImportBatch batch = load(id);
		batch.requireVersion(version);
		return batch;
	}

	/** Corre despues del commit, sin transaccion activa: necesita una propia. */
	private Runnable failWhenBusy(UUID id) {
		TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
		independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return () -> independent.executeWithoutResult(status -> imports.findBatch(id).ifPresent(batch -> {
			batch.failBecauseBusy();
			imports.saveBatch(batch);
		}));
	}

	private UptimeImportBatch load(UUID id) {
		return imports.findBatch(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
