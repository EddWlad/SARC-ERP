package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

import com.tidsec.sarc_bakend.modules.inventory.application.port.EquipmentSheetParser.SheetLine;
import com.tidsec.sarc_bakend.modules.inventory.application.port.EquipmentSheetParser;
import com.tidsec.sarc_bakend.modules.inventory.application.service.EquipmentRowEvaluator.Evaluation;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowCounts;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ConnectorTypeRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportBatchRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportRowRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ExternalIdentityRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFailure;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Trabajo en segundo plano de una carga masiva de equipos: interpretar y clasificar la plantilla
 * para la vista previa, y luego aplicar las filas nuevas y las actualizaciones aprobadas por tramos.
 * Cada fila se vuelve a evaluar al aplicarla: si el inventario cambio desde la vista previa, se
 * omite con el motivo en vez de escribir algo que el analista no reviso.
 */
@Service
class EquipmentImportProcessor {

	private static final Logger log = LoggerFactory.getLogger(EquipmentImportProcessor.class);
	/** Cada fila consulta el inventario: el lote se mantiene acotado. */
	static final int MAX_ROWS = 5_000;
	static final int CHUNK_SIZE = 100;

	private final EquipmentSheetParser parser;
	private final EquipmentRowEvaluator evaluator;
	private final EquipmentImportBatchRepository batches;
	private final EquipmentImportRowRepository rows;
	private final EquipmentRepository equipment;
	private final ExternalIdentityRepository identities;
	private final ConnectorTypeRepository connectorTypes;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor currentActor;
	private final Clock clock;

	EquipmentImportProcessor(EquipmentSheetParser parser, EquipmentRowEvaluator evaluator,
			EquipmentImportBatchRepository batches, EquipmentImportRowRepository rows, EquipmentRepository equipment,
			ExternalIdentityRepository identities, ConnectorTypeRepository connectorTypes, AuditTrail audit,
			TransactionTemplate transactions, CurrentActor currentActor, Clock clock) {
		this.parser = parser;
		this.evaluator = evaluator;
		this.batches = batches;
		this.rows = rows;
		this.equipment = equipment;
		this.identities = identities;
		this.connectorTypes = connectorTypes;
		this.audit = audit;
		this.transactions = transactions;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Interpreta la plantilla y deja la vista previa lista; si no se puede leer, el lote queda fallido. */
	void prepare(UUID batchId, Supplier<List<TabularRow>> original) {
		try {
			List<TabularRow> tabular = original.get();
			transactions.executeWithoutResult(status -> classify(batchId, tabular));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	/** Aplica las filas aplicables por tramos y cierra el lote. */
	void apply(UUID batchId) {
		try {
			while (Boolean.TRUE.equals(transactions.execute(status -> applyNextChunk(batchId)))) {
				// Cada vuelta confirma un tramo; se repite hasta que no quedan filas por aplicar.
			}
			transactions.executeWithoutResult(status -> complete(batchId));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	private void classify(UUID batchId, List<TabularRow> tabular) {
		EquipmentImportBatch batch = load(batchId);
		List<SheetLine> lines = parser.parse(tabular);
		if (lines.isEmpty()) {
			throw new BusinessRuleException("IMPORT_NO_ROWS", "La plantilla no tiene equipos");
		}
		if (lines.size() > MAX_ROWS) {
			throw new BusinessRuleException("IMPORT_TOO_MANY_ROWS",
					"La carga masiva admite hasta " + MAX_ROWS + " equipos por archivo; divida la plantilla");
		}
		Map<String, ConnectorType> catalog = catalog();
		Set<String> seenKeys = new HashSet<>();
		List<EquipmentImportRow> classified = new ArrayList<>();
		for (SheetLine line : lines) {
			classified.add(classifyLine(batchId, line, catalog, seenKeys));
		}
		rows.insertAll(classified);
		EquipmentRowCounts counts = EquipmentRowCounts.of(
				classified.stream().map(EquipmentImportRow::getClassification).toList());
		batch.previewReady(counts);
		batches.save(batch);
		audit.record("EQUIPMENT_IMPORT_PREVIEW_READY", EquipmentImportBatch.AUDIT_ENTITY_TYPE, batchId, Map.of(
				"rows", String.valueOf(counts.total()),
				"new", String.valueOf(counts.newRows()),
				"modified", String.valueOf(counts.modified()),
				"invalid", String.valueOf(counts.invalid()),
				"conflict", String.valueOf(counts.conflict())));
	}

	private EquipmentImportRow classifyLine(UUID batchId, SheetLine sheetLine, Map<String, ConnectorType> catalog,
			Set<String> seenKeys) {
		Evaluation evaluation = evaluator.evaluate(sheetLine.line(), catalog);
		EquipmentRowClassification classification = evaluation.classification();
		List<RowIssue> issues = evaluation.issues();
		if (classification != EquipmentRowClassification.INVALID) {
			String repeated = firstRepeatedKey(sheetLine, catalog, seenKeys);
			if (repeated != null) {
				classification = EquipmentRowClassification.DUPLICATE;
				issues = List.of(new RowIssue("DUPLICATED_IN_BATCH", repeated + " ya aparece en una fila anterior"));
			}
		}
		Equipment existing = evaluation.existing();
		if (existing == null) {
			return EquipmentImportRow.classified(batchId, sheetLine.rowNumber(), sheetLine.line().fitted(),
					classification, issues);
		}
		return EquipmentImportRow.forExisting(batchId, sheetLine.rowNumber(), sheetLine.line().fitted(), classification,
				issues, existing.getId(), existing.getVersion());
	}

	/**
	 * Serie, codigo, UID/PUK e identidades externas no pueden repetirse dentro del lote: dos filas
	 * describirian el mismo equipo y no se sabria cual vale. Devuelve el primero repetido.
	 */
	private static String firstRepeatedKey(SheetLine sheetLine, Map<String, ConnectorType> catalog,
			Set<String> seenKeys) {
		ProposedEquipment proposal = EquipmentLineInterpreter.interpret(sheetLine.line(), catalog).proposal();
		Map<String, String> keys = new HashMap<>();
		keys.put("serial:" + Equipment.normalizeIdentifier(proposal.serial()), "La serie " + proposal.serial());
		if (proposal.assignedCode() != null) {
			keys.put("code:" + proposal.assignedCode().strip(), "El codigo " + proposal.assignedCode().strip());
		}
		if (proposal.uidPuk() != null) {
			keys.put("uid:" + Equipment.normalizeIdentifier(proposal.uidPuk()), "El UID/PUK " + proposal.uidPuk());
		}
		proposal.identities().forEach(identity -> keys.put("identity:" + identity.platform() + "|"
				+ identity.scope().toUpperCase(Locale.ROOT) + "|" + identity.externalId().toUpperCase(Locale.ROOT),
				"La identidad " + identity.describe()));
		String repeated = keys.entrySet().stream().filter(entry -> seenKeys.contains(entry.getKey()))
				.map(Map.Entry::getValue).findFirst().orElse(null);
		seenKeys.addAll(keys.keySet());
		return repeated;
	}

	private boolean applyNextChunk(UUID batchId) {
		List<EquipmentImportRow> next = rows.nextToApply(batchId, CHUNK_SIZE);
		if (next.isEmpty()) {
			return false;
		}
		Map<String, ConnectorType> catalog = catalog();
		for (EquipmentImportRow row : next) {
			Outcome outcome = applyRow(batchId, row, catalog);
			row.recordOutcome(outcome.result(), outcome.detail());
		}
		return true;
	}

	private record Outcome(EquipmentRowOutcome result, String detail) {

		static Outcome skipped(String detail) {
			return new Outcome(EquipmentRowOutcome.SKIPPED, detail);
		}
	}

	private Outcome applyRow(UUID batchId, EquipmentImportRow row, Map<String, ConnectorType> catalog) {
		if (row.getClassification() == EquipmentRowClassification.MODIFIED) {
			Long currentVersion = equipment.findById(row.getEquipmentId()).map(Equipment::getVersion).orElse(null);
			if (!Objects.equals(currentVersion, row.getEquipmentVersion())) {
				return Outcome.skipped("El equipo se modifico despues de la vista previa; vuelva a cargar la plantilla");
			}
		}
		Evaluation evaluation = evaluator.evaluate(row.getLine(), catalog);
		EquipmentRowClassification now = evaluation.classification();
		if (now == EquipmentRowClassification.IDENTICAL) {
			return new Outcome(EquipmentRowOutcome.UNCHANGED, null);
		}
		if (now != row.getClassification()) {
			return Outcome.skipped(evaluation.issues().isEmpty()
					? "El inventario cambio despues de la vista previa; vuelva a cargar la plantilla"
					: evaluation.issues().getFirst().message());
		}
		Equipment written = now == EquipmentRowClassification.NEW ? create(batchId, evaluation)
				: update(batchId, evaluation);
		registerIdentities(written, evaluation);
		return new Outcome(now == EquipmentRowClassification.NEW ? EquipmentRowOutcome.CREATED
				: EquipmentRowOutcome.UPDATED, null);
	}

	private Equipment create(UUID batchId, Evaluation evaluation) {
		ProposedEquipment proposal = evaluation.proposal();
		Equipment created = equipment.save(Equipment.register(proposal.network(), proposal.type(),
				proposal.details(), proposal.connectorTypes(), null));
		Map<String, String> details = new HashMap<>(Map.of(
				"network", created.getNetwork().name(),
				"type", created.getType().name(),
				"serial", created.getSerial(),
				"importBatch", batchId.toString()));
		if (created.getAssignedCode() != null) {
			details.put("assignedCode", created.getAssignedCode());
		}
		audit.record("EQUIPMENT_REGISTERED", Equipment.AUDIT_ENTITY_TYPE, created.getId(), details);
		return created;
	}

	private Equipment update(UUID batchId, Evaluation evaluation) {
		Equipment target = evaluation.existing();
		ProposedEquipment proposal = evaluation.proposal();
		List<ConnectorType> connectors = proposal.connectorTypesOver(target);
		Map<String, String> before = EquipmentChanges.snapshot(target);
		target.applyDetails(proposal.detailsOver(target));
		target.replaceConnectors(connectors);
		equipment.save(target);
		Map<String, String> details = new HashMap<>(EquipmentChanges.diff(before, EquipmentChanges.snapshot(target)));
		details.put("importBatch", batchId.toString());
		audit.record("EQUIPMENT_UPDATED", Equipment.AUDIT_ENTITY_TYPE, target.getId(), details);
		return target;
	}

	/** Las identidades de la plantilla quedan verificadas por quien confirmo el lote. */
	private void registerIdentities(Equipment owner, Evaluation evaluation) {
		UUID actorId = currentActor.id().orElse(null);
		for (ProposedEquipment.Identity proposed : evaluation.identitiesToRegister()) {
			ExternalIdentity identity = ExternalIdentity.register(owner.getId(), proposed.platform(), proposed.scope(),
					proposed.externalId(), null);
			identity.verify(actorId, clock.instant());
			identities.save(identity);
			audit.record("EXTERNAL_IDENTITY_REGISTERED", ExternalIdentity.AUDIT_ENTITY_TYPE, identity.getId(),
					Map.of("equipmentId", owner.getId().toString(), "platform", identity.getPlatform().name(),
							"externalId", identity.getExternalId(), "verified", "true"));
		}
		for (ExternalIdentity identity : evaluation.identitiesToVerify()) {
			identity.verify(actorId, clock.instant());
			identities.save(identity);
			audit.record("EXTERNAL_IDENTITY_VERIFIED", ExternalIdentity.AUDIT_ENTITY_TYPE, identity.getId(), Map.of());
		}
	}

	private void complete(UUID batchId) {
		EquipmentImportBatch batch = load(batchId);
		rows.markUnapprovedAsSkipped(batchId);
		int written = rows.countWritten(batchId);
		batch.complete(written);
		batches.save(batch);
		audit.record("EQUIPMENT_IMPORT_COMPLETED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("written", String.valueOf(written)));
	}

	private void fail(UUID batchId, RuntimeException ex) {
		ImportFailure failure = ImportFailure.of(ex);
		if (failure.unexpected()) {
			log.error("Fallo el procesamiento del lote de equipos {}", batchId, ex);
		}
		transactions.executeWithoutResult(status -> {
			EquipmentImportBatch batch = load(batchId);
			if (batch.isInProgress()) {
				batch.fail(failure.code(), failure.message());
				batches.save(batch);
				audit.record("EQUIPMENT_IMPORT_FAILED", EquipmentImportBatch.AUDIT_ENTITY_TYPE, batchId,
						Map.of("code", failure.code()));
			}
		});
	}

	/** Catalogo completo, inactivos incluidos: un equipo puede conservar un tipo que ya tenia. */
	private Map<String, ConnectorType> catalog() {
		return connectorTypes.findAll().stream()
				.collect(Collectors.toMap(ConnectorType::getCode, Function.identity()));
	}

	private EquipmentImportBatch load(UUID batchId) {
		return batches.findById(batchId)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
