package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.inventory.application.command.EquipmentRowDecisionCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.command.PasteEquipmentImportCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.command.UploadEquipmentImportCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.port.EquipmentImportTemplate;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorTypeRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportBatchRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportRowRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFiles;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * Carga masiva de equipos (RF-002, PRD RF-005.1): descargar la plantilla, cargarla o pegarla,
 * revisar la vista previa, aprobar las actualizaciones y confirmar. Interpretar y aplicar ocurren en
 * segundo plano, como en la importacion de consumos. Solo administradores y analistas.
 */
@Service
@Transactional
@PreAuthorize(InventoryPermissions.EDITORS)
public class EquipmentImportService {

	private static final String STORAGE_FOLDER = "imports/equipment/";
	private static final String PASTE_FILE_NAME = "pegado.txt";

	private final EquipmentImportBatchRepository batches;
	private final EquipmentImportRowRepository rows;
	private final EquipmentImportProcessor processor;
	private final ConnectorTypeRepository connectorTypes;
	private final EquipmentImportTemplate template;
	private final TabularReader reader;
	private final FileStorage fileStorage;
	private final ImportJobs jobs;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor currentActor;
	private final Clock clock;

	EquipmentImportService(EquipmentImportBatchRepository batches, EquipmentImportRowRepository rows,
			EquipmentImportProcessor processor, ConnectorTypeRepository connectorTypes, EquipmentImportTemplate template,
			TabularReader reader, FileStorage fileStorage, ImportJobs jobs, AuditTrail audit,
			TransactionTemplate transactions, CurrentActor currentActor, Clock clock) {
		this.batches = batches;
		this.rows = rows;
		this.processor = processor;
		this.connectorTypes = connectorTypes;
		this.template = template;
		this.reader = reader;
		this.fileStorage = fileStorage;
		this.jobs = jobs;
		this.audit = audit;
		this.transactions = transactions;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Plantilla XLSX con las columnas que se leen y los tipos de conector activos del catalogo. */
	@Transactional(readOnly = true)
	public ReportFile template() {
		return template.render(connectorTypes.findAll().stream().filter(ConnectorType::isActive).toList());
	}

	/** Carga la plantilla (XLSX/CSV); la vista previa se prepara en segundo plano. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public EquipmentImportBatch upload(UploadEquipmentImportCommand command) {
		byte[] content = command.content();
		ImportFiles.requireContent(content.length);
		return open(ImportOrigin.FILE, ImportFiles.safeName(command.fileName(), "equipos"), content,
				() -> reader.read(content));
	}

	/** Filas pegadas desde la plantilla, con su encabezado. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public EquipmentImportBatch paste(PasteEquipmentImportCommand command) {
		String text = command.text() == null ? "" : command.text();
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		ImportFiles.requireContent(text.isBlank() ? 0 : content.length);
		return open(ImportOrigin.PASTE, PASTE_FILE_NAME, content, () -> reader.readText(text));
	}

	@Transactional(readOnly = true)
	public EquipmentImportBatch get(UUID id) {
		return load(id);
	}

	@Transactional(readOnly = true)
	public PageResult<EquipmentImportBatch> history(PageQuery pageQuery) {
		return batches.search(pageQuery);
	}

	@Transactional(readOnly = true)
	public PageResult<EquipmentImportRow> rows(UUID batchId, EquipmentRowClassification classification,
			PageQuery pageQuery) {
		load(batchId);
		return rows.search(batchId, classification, pageQuery);
	}

	/** Aprueba o no la actualizacion de equipos existentes mientras la vista previa esta abierta. */
	public List<EquipmentImportRow> decide(EquipmentRowDecisionCommand command) {
		load(command.batchId()).requireReviewable();
		Set<UUID> requested = Set.copyOf(command.rowIds());
		List<EquipmentImportRow> found = rows.findByIds(command.batchId(), requested);
		if (found.size() != requested.size()) {
			throw new NotFoundException("IMPORT_ROW_NOT_FOUND", "Alguna fila no pertenece a este lote");
		}
		found.forEach(row -> row.decide(command.approved()));
		return found;
	}

	/** Aplica las filas nuevas y las actualizaciones aprobadas en segundo plano. */
	public EquipmentImportBatch confirm(UUID id, long version) {
		EquipmentImportBatch batch = loadForChange(id, version);
		batch.confirm(currentActor.id().orElse(null), clock.instant());
		batches.save(batch);
		audit.record("EQUIPMENT_IMPORT_CONFIRMED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	/** Reanuda un lote que fallo al confirmarse; continua con las filas que faltaban. */
	public EquipmentImportBatch retry(UUID id, long version) {
		EquipmentImportBatch batch = loadForChange(id, version);
		batch.retry();
		batches.save(batch);
		audit.record("EQUIPMENT_IMPORT_RETRIED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	public EquipmentImportBatch discard(UUID id, long version) {
		EquipmentImportBatch batch = loadForChange(id, version);
		batch.discard();
		batches.save(batch);
		audit.record("EQUIPMENT_IMPORT_DISCARDED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		return batch;
	}

	/**
	 * El original se guarda antes de abrir la transaccion para no retener una conexion durante la
	 * subida. Si luego no se pudiera registrar el lote, el original queda sin referencia en el
	 * almacenamiento privado; no se expone ni se procesa.
	 */
	private EquipmentImportBatch open(ImportOrigin origin, String fileName, byte[] content,
			Supplier<List<TabularRow>> original) {
		UUID id = UUID.randomUUID();
		String reference = fileStorage.storeDocument(STORAGE_FOLDER + id, fileName, content);
		return transactions.execute(status -> {
			EquipmentImportBatch batch = batches.save(EquipmentImportBatch.open(id, origin, fileName,
					ContentFingerprint.sha256(content), reference));
			audit.record("EQUIPMENT_IMPORT_UPLOADED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, id, Map.of(
					"origin", origin.name(),
					"fileName", fileName,
					"sha256", batch.getChecksum()));
			jobs.submitAfterCommit(() -> processor.prepare(id, original), failWhenBusy(id));
			return batch;
		});
	}

	/**
	 * Si la cola de trabajos esta llena el lote falla de inmediato y el analista lo reintenta luego. Corre
	 * despues del commit del lote, cuando ya no hay transaccion activa: necesita una propia.
	 */
	private Runnable failWhenBusy(UUID id) {
		TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
		independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return () -> independent.executeWithoutResult(status -> batches.findById(id).ifPresent(batch -> {
			batch.failBecauseBusy();
			batches.save(batch);
		}));
	}

	private EquipmentImportBatch loadForChange(UUID id, long version) {
		EquipmentImportBatch batch = load(id);
		batch.requireVersion(version);
		return batch;
	}

	private EquipmentImportBatch load(UUID id) {
		return batches.findById(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
