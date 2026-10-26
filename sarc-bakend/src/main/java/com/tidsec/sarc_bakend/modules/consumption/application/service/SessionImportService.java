package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.consumption.application.command.PasteImportCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.command.RowDecisionCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.command.UploadImportCommand;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportBatchRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportRowRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFiles;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * Importacion de sesiones (PRD 3.1 y RF-003): cargar el original, revisar la vista previa, decidir
 * y confirmar. Interpretar y confirmar ocurren en segundo plano; el cliente consulta el estado del
 * lote. Solo administradores y analistas.
 */
@Service
@Transactional
@PreAuthorize(ConsumptionPermissions.EDITORS)
public class SessionImportService {

	private static final String STORAGE_FOLDER = "imports/energy/";
	private static final String PASTE_FILE_NAME = "pegado.txt";

	private final ImportBatchRepository batches;
	private final ImportRowRepository rows;
	private final ImportProcessor processor;
	private final TabularReader reader;
	private final FileStorage fileStorage;
	private final ImportJobs jobs;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor currentActor;
	private final Clock clock;

	SessionImportService(ImportBatchRepository batches, ImportRowRepository rows, ImportProcessor processor,
			TabularReader reader, FileStorage fileStorage, ImportJobs jobs, AuditTrail audit,
			TransactionTemplate transactions, CurrentActor currentActor, Clock clock) {
		this.batches = batches;
		this.rows = rows;
		this.processor = processor;
		this.reader = reader;
		this.fileStorage = fileStorage;
		this.jobs = jobs;
		this.audit = audit;
		this.transactions = transactions;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Carga un CSV/XLSX original; la vista previa se prepara en segundo plano. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportBatch upload(UploadImportCommand command) {
		byte[] content = command.content();
		ImportFiles.requireContent(content.length);
		return open(command.source(), command.scope(), ImportOrigin.FILE,
				ImportFiles.safeName(command.fileName(), "importacion"), content,
				() -> reader.read(content));
	}

	/** Filas pegadas desde la hoja de la plataforma, con su encabezado. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportBatch paste(PasteImportCommand command) {
		String text = command.text() == null ? "" : command.text();
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		ImportFiles.requireContent(text.isBlank() ? 0 : content.length);
		return open(command.source(), command.scope(), ImportOrigin.PASTE, PASTE_FILE_NAME, content,
				() -> reader.readText(text));
	}

	@Transactional(readOnly = true)
	public ImportBatch get(UUID id) {
		return load(id);
	}

	@Transactional(readOnly = true)
	public PageResult<ImportBatch> history(PageQuery pageQuery) {
		return batches.search(pageQuery);
	}

	@Transactional(readOnly = true)
	public PageResult<ImportRow> rows(UUID batchId, RowClassification classification, PageQuery pageQuery) {
		load(batchId);
		return rows.search(batchId, classification, pageQuery);
	}

	/** Marca filas como prueba o aprueba correcciones mientras la vista previa esta abierta. */
	public List<ImportRow> decide(RowDecisionCommand command) {
		if (command.test() == null && command.approved() == null) {
			throw new BusinessRuleException("ROW_DECISION_EMPTY", "Indique si las filas son de prueba o si se aprueban");
		}
		load(command.batchId()).requireReviewable();
		Set<UUID> requested = Set.copyOf(command.rowIds());
		List<ImportRow> found = rows.findByIds(command.batchId(), requested);
		if (found.size() != requested.size()) {
			throw new NotFoundException("IMPORT_ROW_NOT_FOUND", "Alguna fila no pertenece a este lote");
		}
		found.forEach(row -> row.decide(command.test(), command.approved()));
		return found;
	}

	/** Incorpora las filas aplicables en segundo plano. */
	public ImportBatch confirm(UUID id, long version) {
		ImportBatch batch = loadForChange(id, version);
		batch.confirm(currentActor.id().orElse(null), clock.instant());
		batches.save(batch);
		audit.record("ENERGY_IMPORT_CONFIRMED", ImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	/** Reanuda un lote que fallo al confirmarse; continua con las filas que faltaban. */
	public ImportBatch retry(UUID id, long version) {
		ImportBatch batch = loadForChange(id, version);
		batch.retry();
		batches.save(batch);
		audit.record("ENERGY_IMPORT_RETRIED", ImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	public ImportBatch discard(UUID id, long version) {
		ImportBatch batch = loadForChange(id, version);
		batch.discard();
		batches.save(batch);
		audit.record("ENERGY_IMPORT_DISCARDED", ImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		return batch;
	}

	/**
	 * El original se guarda antes de abrir la transaccion para no retener una conexion durante la
	 * subida. Si luego no se pudiera registrar el lote, el original queda sin referencia en el
	 * almacenamiento privado; no se expone ni se procesa.
	 */
	private ImportBatch open(SessionSource source, String scope, ImportOrigin origin, String fileName, byte[] content,
			Supplier<List<TabularRow>> original) {
		if (source == null) {
			throw new BusinessRuleException("IMPORT_SOURCE_REQUIRED", "Indique la plataforma de origen");
		}
		UUID id = UUID.randomUUID();
		String reference = fileStorage.storeDocument(STORAGE_FOLDER + id, fileName, content);
		return transactions.execute(status -> {
			ImportBatch batch = batches.save(
					ImportBatch.open(id, source, scope, origin, fileName, ContentFingerprint.sha256(content), reference));
			audit.record("ENERGY_IMPORT_UPLOADED", ImportBatch.AUDIT_ENTITY_TYPE, id, Map.of(
					"source", source.name(),
					"origin", origin.name(),
					"fileName", fileName,
					"sha256", batch.getChecksum()));
			jobs.submitAfterCommit(() -> processor.prepare(id, original), failWhenBusy(id));
			return batch;
		});
	}

	private ImportBatch loadForChange(UUID id, long version) {
		ImportBatch batch = load(id);
		batch.requireVersion(version);
		return batch;
	}

	/** Si la cola de trabajos esta llena el lote falla de inmediato y el analista lo reintenta luego. */
	private Runnable failWhenBusy(UUID id) {
		return () -> transactions.executeWithoutResult(status -> batches.findById(id).ifPresent(batch -> {
			batch.failBecauseBusy();
			batches.save(batch);
		}));
	}

	private ImportBatch load(UUID id) {
		return batches.findById(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
