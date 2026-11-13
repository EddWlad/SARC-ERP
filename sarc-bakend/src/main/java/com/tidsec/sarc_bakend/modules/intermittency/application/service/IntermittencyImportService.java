package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;
import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyRowParser;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.PendingRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.RowChange;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.ImportFormat;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowEdit;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.RowResolution;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFiles;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/**
 * Ciclo de vida de los lotes de importacion de intermitencias: cargar, revisar y corregir en la vista previa,
 * decidir, confirmar, descartar y reintentar. El trabajo pesado corre en {@link IntermittencyImportProcessor}.
 * Todo cambio de una fila reclasifica las filas relacionadas, para que los conflictos de una decision retirada
 * nunca queden pegados (FR-026, FR-027).
 */
@Service
@Transactional
@PreAuthorize(IntermittencyPermissions.EDITORS)
public class IntermittencyImportService {

	private static final String STORAGE_FOLDER = "imports/intermittency/";
	private static final String PASTE_FILE_NAME = "pegado.txt";
	private static final int UNRESOLVED_SAMPLE = 20;

	private final IntermittencyImportRepository imports;
	private final IntermittencyImportProcessor processor;
	private final ImportClassifier classifier;
	private final IntermittencyRowParser parser;
	private final KiaPointDirectory points;
	private final FileStorage fileStorage;
	private final ImportJobs jobs;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor actor;
	private final Clock clock;

	IntermittencyImportService(IntermittencyImportRepository imports, IntermittencyImportProcessor processor,
			ImportClassifier classifier, IntermittencyRowParser parser, KiaPointDirectory points,
			FileStorage fileStorage, ImportJobs jobs, AuditTrail audit, TransactionTemplate transactions,
			CurrentActor actor, Clock clock) {
		this.imports = imports;
		this.processor = processor;
		this.classifier = classifier;
		this.parser = parser;
		this.points = points;
		this.fileStorage = fileStorage;
		this.jobs = jobs;
		this.audit = audit;
		this.transactions = transactions;
		this.actor = actor;
		this.clock = clock;
	}

	// ----- Carga -----

	/** XLSX o CSV de hasta 20 MiB; la vista previa se prepara en segundo plano. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public IntermittencyImportBatch upload(ImportCommands.Upload command) {
		byte[] content = command.content();
		ImportFiles.requireContent(content.length);
		ImportFormat format = detectFormat(content);
		String fileName = ImportFiles.safeName(command.fileName(), "intermitencias");
		return open(format, fileName, content, () -> parser.parse(content));
	}

	/** Bloque pegado desde una hoja de calculo (tabuladores), con la misma interpretacion. */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public IntermittencyImportBatch paste(ImportCommands.Paste command) {
		String text = command.text() == null ? "" : command.text();
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		ImportFiles.requireContent(text.isBlank() ? 0 : content.length);
		return open(ImportFormat.PASTE, PASTE_FILE_NAME, content, () -> parser.parseText(text));
	}

	@Transactional(readOnly = true)
	public IntermittencyImportBatch get(UUID id) {
		return load(id);
	}

	@Transactional(readOnly = true)
	public PageResult<IntermittencyImportBatch> history(PageQuery page) {
		return imports.searchBatches(page);
	}

	@Transactional(readOnly = true)
	public PageResult<IntermittencyImportRow> rows(UUID batchId, RowClassification classification, PageQuery page) {
		load(batchId);
		return imports.searchRows(batchId, classification, page);
	}

	// ----- Vista previa -----

	/** Corrige la fila, la vuelve a interpretar y reclasifica las filas del mismo punto (FR-026). */
	public RowChange editRow(ImportCommands.EditRow command) {
		IntermittencyImportBatch batch = load(command.batchId());
		batch.requireReviewable();
		IntermittencyImportRow row = loadRow(batch, command.rowId(), command.version());
		RowInterpretation current = row.getInterpretation();
		List<RowEdit> edits = translate(row, current, command);
		if (edits.isEmpty()) {
			throw new BusinessRuleException("IMPORT_EDIT_EMPTY", "La edicion no cambia nada");
		}
		requireNoOpenTest(current, edits);
		UUID previousPoint = row.getEquipmentId();
		row.addEdits(edits);
		reclassify(batch, row, previousPoint);
		audit.record("INTERMITTENCY_IMPORT_ROW_EDITED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("row", String.valueOf(row.getRowNumber()), "edits", String.valueOf(edits.size())));
		return new RowChange(row, batch);
	}

	/** Vincula la fila a un punto KIA elegido; el nombre del archivo solo es una sugerencia (FR-003). */
	public RowChange linkRow(ImportCommands.LinkRow command) {
		IntermittencyImportBatch batch = load(command.batchId());
		batch.requireReviewable();
		IntermittencyImportRow row = loadRow(batch, command.rowId(), command.version());
		requireLinkable(command.equipmentId());
		UUID previousPoint = row.getEquipmentId();
		UUID author = actor.id().orElse(null);
		row.link(command.equipmentId(), author, Instant.now(clock));
		row.addEdits(List.of(edit(RowEdit.LINK, null, null, previousPoint == null ? null : previousPoint.toString(),
				command.equipmentId().toString())));
		reclassify(batch, row, previousPoint);
		audit.record("INTERMITTENCY_IMPORT_ROW_LINKED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("row", String.valueOf(row.getRowNumber()), "equipmentId", command.equipmentId().toString()));
		return new RowChange(row, batch);
	}

	/** Decide la fila y cada ventana: aplicar, omitir con motivo, dejar pendiente, y las resoluciones (FR-027). */
	public RowChange decide(ImportCommands.DecideRow command) {
		IntermittencyImportBatch batch = load(command.batchId());
		batch.requireReviewable();
		IntermittencyImportRow row = loadRow(batch, command.rowId(), command.version());
		RowDecision decision = command.decision() == null ? RowDecision.APPLY : command.decision();
		if (decision == RowDecision.KEEP_PENDING && row.getClassification() != RowClassification.PENDING_LINK
				&& row.getClassification() != RowClassification.CONFLICT) {
			throw new BusinessRuleException("KEEP_PENDING_NOT_ALLOWED",
					"Solo una fila con el punto por vincular o en conflicto puede quedar pendiente");
		}
		UUID point = row.getEquipmentId();
		applyDecision(row, decision, command.skipReason(), command.acknowledgeTotal(), command.windows());
		reclassify(batch, row, point);
		audit.record("INTERMITTENCY_IMPORT_ROW_DECIDED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("row", String.valueOf(row.getRowNumber()), "decision", decision.name()));
		return new RowChange(row, batch);
	}

	/**
	 * Aplica a la fila las resoluciones por ventana y el destino de la fila, y deja la traza de cada decision
	 * (FR-027 a FR-029). Se usa en la vista previa y al resolver una fila pendiente de un lote confirmado.
	 */
	private void applyDecision(IntermittencyImportRow row, RowDecision decision, String skipReason,
			boolean acknowledgeTotal, List<ImportCommands.WindowDecision> requestedWindows) {
		// Las ventanas que se vuelven a decidir se evaluan sin su decision anterior: una ventana que se conserva
		// o se omite deja de mostrar su conflicto, y asi se podria cambiar de decision sin retirarla antes.
		Map<Integer, String> before = new HashMap<>();
		row.getInterpretation().windows().stream().filter(window -> window.decision() != null)
				.forEach(window -> before.put(window.pair(), window.decision().name()));
		Set<Integer> requestedPairs = new HashSet<>();
		requestedWindows.forEach(requested -> requestedPairs.add(requested.pair()));
		RowInterpretation cleared = row.getInterpretation().withWindows(row.getInterpretation().windows().stream()
				.map(window -> requestedPairs.contains(window.pair()) ? window.withDecision(null) : window).toList());
		row.classified(cleared, row.getClassification(), row.getEquipmentId(), row.getTouchedDays());
		classifier.classify(List.of(row));
		RowInterpretation interpretation = row.getInterpretation();
		List<RowEdit> trace = new ArrayList<>();
		Map<Integer, WindowResolution> resolutions = new HashMap<>();
		for (ImportCommands.WindowDecision requested : requestedWindows) {
			RowWindow window = interpretation.windows().stream().filter(w -> w.pair() == requested.pair())
					.findFirst().orElseThrow(() -> new NotFoundException("WINDOW_PAIR_NOT_FOUND",
							"La fila no tiene el par " + requested.pair()));
			requireApplicable(interpretation, window, requested);
			resolutions.put(window.pair(), requested.resolution());
			trace.add(edit(RowEdit.DECISION, window.pair(),
					requested.resolution() == null ? "CLEARED" : requested.resolution().name(),
					before.get(window.pair()), requested.justification()));
		}
		List<RowWindow> updated = interpretation.windows().stream()
				.map(window -> resolutions.containsKey(window.pair()) ? window.withDecision(resolutions.get(window.pair()))
						: window).toList();
		RowInterpretation decided = interpretation.withWindows(updated);
		if (acknowledgeTotal) {
			decided = decided.withTotalAcknowledged(true);
			trace.add(edit(RowEdit.ACKNOWLEDGE_TOTAL, null, null, null, "true"));
		}
		trace.add(edit(RowEdit.ROW_DECISION, null, decision.name(),
				row.getDecision() == null ? null : row.getDecision().name(), skipReason));
		row.decide(decision, skipReason);
		row.addEdits(trace);
		row.classified(decided, row.getClassification(), row.getEquipmentId(), row.getTouchedDays());
	}

	// ----- Confirmacion -----

	/**
	 * Vuelve a validar todo (instantes futuros, conflictos con lo guardado) y, si no queda nada por resolver,
	 * empieza a crear las ventanas en segundo plano (FR-021b, FR-028). Las filas INVALID o en CONFLICT sin
	 * decision bloquean la confirmacion.
	 */
	public IntermittencyImportBatch confirm(UUID id, long version) {
		IntermittencyImportBatch batch = loadForChange(id, version);
		batch.requireReviewable();
		List<IntermittencyImportRow> rows = imports.findRows(id);
		classifier.classify(rows);
		List<Integer> unresolved = rows.stream().filter(row -> !RowResolution.isResolved(row.getDecision(),
				row.getClassification(), row.getInterpretation())).map(IntermittencyImportRow::getRowNumber).toList();
		if (!unresolved.isEmpty()) {
			throw IntermittencyImportProcessor.unresolved(unresolved.size(),
					unresolved.subList(0, Math.min(UNRESOLVED_SAMPLE, unresolved.size())));
		}
		rows.forEach(imports::saveRow);
		batch.confirm(actor.id().orElse(null), Instant.now(clock));
		batch.recount(imports.countRows(id), batch.getEarliestDay(), batch.getLatestDay());
		imports.saveBatch(batch);
		audit.record("INTERMITTENCY_IMPORT_CONFIRMED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, id,
				Map.of("rows", String.valueOf(rows.size())));
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	/** Continua con las filas que aun no tienen ventana creada. */
	public IntermittencyImportBatch retry(UUID id, long version) {
		IntermittencyImportBatch batch = loadForChange(id, version);
		batch.retry();
		imports.saveBatch(batch);
		audit.record("INTERMITTENCY_IMPORT_RETRIED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		jobs.submitAfterCommit(() -> processor.apply(id), failWhenBusy(id));
		return batch;
	}

	public IntermittencyImportBatch discard(UUID id, long version) {
		IntermittencyImportBatch batch = loadForChange(id, version);
		batch.discard();
		imports.saveBatch(batch);
		audit.record("INTERMITTENCY_IMPORT_DISCARDED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, id, Map.of());
		return batch;
	}

	// ----- Filas pendientes de lotes confirmados -----

	/** Filas KEEP_PENDING de lotes COMPLETED sin resolver, con su lote (FR-028). */
	@Transactional(readOnly = true)
	public PageResult<PendingRow> pendingRows(LocalDate from, LocalDate to, RowClassification classification,
			PageQuery page) {
		Map<UUID, IntermittencyImportBatch> batches = new HashMap<>();
		return imports.searchPendingRows(from, to, classification, Set.of(), page).map(row -> new PendingRow(row,
				batches.computeIfAbsent(row.getBatchId(), id -> load(id))));
	}

	/**
	 * Resuelve una fila pendiente de un lote ya confirmado: vincula el punto, corrige, decide cada ventana y la
	 * aplica u omite. Vuelve a validar duplicados, solapamientos, ventana larga e instante futuro, y crea las
	 * ventanas bajo el bloqueo por punto. Queda con quien la resolvio y cuando (FR-028, US3-4).
	 */
	public RowChange resolve(ImportCommands.ResolvePending command) {
		IntermittencyImportBatch batch = load(command.batchId());
		if (batch.getStatus() != com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus.COMPLETED) {
			throw new BusinessRuleException("IMPORT_NOT_COMPLETED",
					"Solo se resuelven filas de un lote ya confirmado; en la vista previa se decide la fila directamente");
		}
		IntermittencyImportRow row = loadRow(batch, command.rowId(), command.version());
		if (row.getDecision() != RowDecision.KEEP_PENDING || row.getResolvedAt() != null) {
			throw new BusinessRuleException("IMPORT_ROW_NOT_PENDING", "La fila no esta pendiente de resolver");
		}
		RowDecision decision = command.decision() == null ? RowDecision.APPLY : command.decision();
		if (decision == RowDecision.KEEP_PENDING) {
			throw new BusinessRuleException("KEEP_PENDING_NOT_ALLOWED",
					"Al resolver una fila pendiente se aplica o se omite con motivo");
		}
		UUID author = actor.id().orElse(null);
		Instant now = Instant.now(clock);
		if (command.equipmentId() != null) {
			requireLinkable(command.equipmentId());
			row.link(command.equipmentId(), author, now);
			row.addEdits(List.of(edit(RowEdit.LINK, null, null, null, command.equipmentId().toString())));
		}
		if (!command.edits().isEmpty()) {
			List<RowEdit> edits = translate(row, row.getInterpretation(), new ImportCommands.EditRow(batch.getId(),
					row.getId(), command.version(), command.edits(), List.of(), List.of()));
			requireNoOpenTest(row.getInterpretation(), edits);
			row.addEdits(edits);
		}
		applyDecision(row, decision, command.skipReason(), command.acknowledgeTotal(), command.windows());
		classifier.classify(List.of(row));
		if (decision == RowDecision.APPLY) {
			if (row.getEquipmentId() == null || !RowResolution.isResolved(RowDecision.APPLY, row.getClassification(),
					row.getInterpretation())) {
				throw IntermittencyImportProcessor.unresolved(1, List.of(row.getRowNumber()));
			}
			processor.applyResolved(batch, row, author);
		}
		row.resolved(author, now);
		imports.saveRow(row);
		audit.record("INTERMITTENCY_IMPORT_ROW_RESOLVED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("row", String.valueOf(row.getRowNumber()), "decision", decision.name()));
		return new RowChange(row, batch);
	}

	// ----- Internos -----

	/** El original se guarda antes de abrir la transaccion: no retiene una conexion durante la subida. */
	private IntermittencyImportBatch open(ImportFormat format, String fileName, byte[] content,
			java.util.function.Supplier<List<com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow>> parsed) {
		UUID id = UUID.randomUUID();
		String reference = fileStorage.storeDocument(STORAGE_FOLDER + id, fileName, content);
		return transactions.execute(status -> {
			IntermittencyImportBatch batch = imports.saveBatch(IntermittencyImportBatch.open(id, format, fileName,
					ContentFingerprint.sha256(content), reference));
			audit.record("INTERMITTENCY_IMPORT_CREATED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, id,
					Map.of("format", format.name(), "sha256", batch.getChecksum()));
			jobs.submitAfterCommit(() -> processor.prepare(id, parsed), failWhenBusy(id));
			return batch;
		});
	}

	private static ImportFormat detectFormat(byte[] content) {
		boolean zip = content.length >= 4 && content[0] == 'P' && content[1] == 'K' && content[2] == 3
				&& content[3] == 4;
		boolean ole = content.length >= 4 && (content[0] & 0xFF) == 0xD0 && (content[1] & 0xFF) == 0xCF
				&& content[2] == 0x11 && (content[3] & 0xFF) == 0xE0;
		if (ole) {
			throw new BusinessRuleException("IMPORT_FORMAT_UNSUPPORTED",
					"Los archivos .xls no se admiten; guarde el registro como XLSX o CSV");
		}
		return zip ? ImportFormat.XLSX : ImportFormat.CSV;
	}

	/** Reclasifica la fila editada y las demas del lote que comparten punto con ella, antes o despues del cambio. */
	private void reclassify(IntermittencyImportBatch batch, IntermittencyImportRow row, UUID previousPoint) {
		classifier.classify(List.of(row));
		Set<UUID> pointIds = new HashSet<>();
		if (previousPoint != null) {
			pointIds.add(previousPoint);
		}
		if (row.getEquipmentId() != null) {
			pointIds.add(row.getEquipmentId());
		}
		Map<UUID, IntermittencyImportRow> related = new LinkedHashMap<>();
		related.put(row.getId(), row);
		imports.findRows(batch.getId(), pointIds).forEach(other -> related.putIfAbsent(other.getId(), other));
		classifier.classify(new ArrayList<>(related.values()));
		LocalDate earliest = batch.getEarliestDay();
		LocalDate latest = batch.getLatestDay();
		for (IntermittencyImportRow changed : related.values()) {
			imports.saveRow(changed);
			for (LocalDate day : changed.getTouchedDays()) {
				earliest = earliest == null || day.isBefore(earliest) ? day : earliest;
				latest = latest == null || day.isAfter(latest) ? day : latest;
			}
		}
		batch.recount(imports.countRows(batch.getId()), earliest, latest);
		imports.saveBatch(batch);
	}

	private List<RowEdit> translate(IntermittencyImportRow row, RowInterpretation current,
			ImportCommands.EditRow command) {
		List<RowEdit> edits = new ArrayList<>();
		for (ImportCommands.Edit item : command.edits()) {
			String target = item.target() == null ? "" : item.target();
			switch (target) {
				case RowEdit.DATE -> edits.add(edit(RowEdit.DATE, null, null, current.date(), item.value()));
				case RowEdit.CODE -> edits.add(edit(RowEdit.CODE, null, null, current.code(), item.value()));
				case RowEdit.COMMENT -> edits.add(edit(RowEdit.COMMENT, null, null, current.comment(), item.value()));
				case RowEdit.TEST -> edits.add(edit(RowEdit.TEST, item.pair(), null, null,
						String.valueOf(Boolean.parseBoolean(item.value()))));
				case RowEdit.WINDOW -> windowEdits(current, item, edits);
				default -> throw invalid("La edicion «" + target + "» no existe");
			}
		}
		int next = 1 + java.util.stream.Stream.concat(row.getRaw().pairs().stream(),
				java.util.stream.Stream.concat(current.windows().stream().map(RowWindow::pair),
						row.getEdits().stream().filter(e -> RowEdit.ADD_WINDOW.equals(e.target())).map(RowEdit::pair)))
				.mapToInt(Integer::intValue).max().orElse(0);
		for (ImportCommands.NewWindow added : command.addWindows()) {
			requireInstant(added.startAt());
			edits.add(edit(RowEdit.ADD_WINDOW, next, RowEdit.START_AT, null, added.startAt()));
			if (added.endAt() != null) {
				requireInstant(added.endAt());
				edits.add(edit(RowEdit.ADD_WINDOW, next, RowEdit.END_AT, null, added.endAt()));
			}
			next++;
		}
		for (Integer pair : command.removePairs()) {
			if (current.windows().stream().noneMatch(window -> window.pair() == pair)) {
				throw new NotFoundException("WINDOW_PAIR_NOT_FOUND", "La fila no tiene el par " + pair);
			}
			edits.add(edit(RowEdit.REMOVE_PAIR, pair, null, null, null));
		}
		return edits;
	}

	private void windowEdits(RowInterpretation current, ImportCommands.Edit item, List<RowEdit> edits) {
		RowWindow window = current.windows().stream().filter(w -> item.pair() != null && w.pair() == item.pair())
				.findFirst().orElseThrow(() -> new NotFoundException("WINDOW_PAIR_NOT_FOUND",
						"La fila no tiene el par indicado"));
		if (item.startAt() != null) {
			requireInstant(item.startAt());
			edits.add(edit(RowEdit.WINDOW, window.pair(), RowEdit.START_AT, window.startAt(), item.startAt()));
		}
		if (item.endAt() != null) {
			requireInstant(item.endAt());
			edits.add(edit(RowEdit.WINDOW, window.pair(), RowEdit.END_AT, window.endAt(), item.endAt()));
		}
		if (item.endsNextDay() != null) {
			edits.add(edit(RowEdit.WINDOW, window.pair(), RowEdit.ENDS_NEXT_DAY,
					String.valueOf(window.endsNextDay()), String.valueOf(item.endsNextDay())));
		}
	}

	/** Marcar como prueba un par con la decision OPEN se rechaza, en cualquier orden (FR-016). */
	private static void requireNoOpenTest(RowInterpretation current, List<RowEdit> edits) {
		for (RowEdit edit : edits) {
			if (!RowEdit.TEST.equals(edit.target()) || !Boolean.parseBoolean(edit.value())) {
				continue;
			}
			boolean openMarked = current.windows().stream().filter(window -> edit.pair() == null
					|| window.pair() == edit.pair()).anyMatch(window -> window.decision() == WindowResolution.OPEN);
			if (openMarked) {
				throw openTestNotAllowed();
			}
		}
	}

	private static void requireApplicable(RowInterpretation interpretation, RowWindow window,
			ImportCommands.WindowDecision requested) {
		WindowResolution resolution = requested.resolution();
		if (resolution == null || resolution == WindowResolution.OMIT) {
			return;
		}
		switch (resolution) {
			case OPEN -> {
				if (window.test()) {
					throw openTestNotAllowed();
				}
				boolean openable = window.startAt() != null && (window.has(IssueCode.INCOMPLETE_PAIR)
						|| window.decision() == WindowResolution.OPEN);
				if (!openable) {
					throw notApplicable("Solo un par al que le falta el fin puede quedar abierto");
				}
			}
			case KEEP_EXISTING -> {
				if (!window.has(IssueCode.OVERLAPS_EXISTING)) {
					throw notApplicable("La ventana no se solapa con una ya registrada");
				}
			}
			case REPLACE -> {
				if (!window.has(IssueCode.OVERLAPS_EXISTING) || window.conflictWindowId() == null) {
					throw notApplicable("Solo se reemplaza una ventana que se solapa con una sola ya registrada");
				}
				if (requested.justification() == null || requested.justification().isBlank()) {
					throw new BusinessRuleException("JUSTIFICATION_REQUIRED",
							"Reemplazar una ventana registrada exige una justificacion");
				}
			}
			case MERGE -> {
				long overlapping = interpretation.active().stream()
						.filter(other -> other.has(IssueCode.OVERLAP_IN_BATCH)).count();
				if (!window.has(IssueCode.OVERLAP_IN_BATCH) || overlapping < 2) {
					throw notApplicable("Solo se fusionan ventanas solapadas de la misma fila");
				}
			}
			default -> {
				// OMIT ya se acepto arriba.
			}
		}
	}

	private void requireLinkable(UUID equipmentId) {
		KiaPointReference point = points.findByIds(List.of(equipmentId)).get(equipmentId);
		if (point == null) {
			throw new NotFoundException("POINT_NOT_FOUND", "El punto de carga no existe");
		}
		if (!point.inScope()) {
			throw new BusinessRuleException("NOT_KIA_CHARGING_POINT",
					"El equipo no es un punto de carga KIA; solo se vinculan puntos KIA");
		}
		if (point.adminStatus() == AdministrativeStatus.DELETED) {
			throw new BusinessRuleException("DELETED_EQUIPMENT",
					"El punto esta eliminado; recuperelo desde Inventario antes de vincularlo");
		}
	}

	private RowEdit edit(String target, Integer pair, String field, String previous, String value) {
		return new RowEdit(target, pair, field, previous, value, actor.id().orElse(null),
				Instant.now(clock).toString());
	}

	private static void requireInstant(String text) {
		try {
			OffsetDateTime.parse(text);
		}
		catch (DateTimeParseException | NullPointerException ex) {
			throw invalid("«" + text + "» no es un instante con fecha, hora y desfase");
		}
	}

	private static BusinessRuleException invalid(String message) {
		return new BusinessRuleException("IMPORT_EDIT_INVALID", message);
	}

	private static BusinessRuleException notApplicable(String message) {
		return new BusinessRuleException("RESOLUTION_NOT_APPLICABLE", message);
	}

	private static BusinessRuleException openTestNotAllowed() {
		return new BusinessRuleException("OPEN_TEST_NOT_ALLOWED",
				"Una ventana de prueba debe estar cerrada; registre su fin o marquela como prueba despues de cerrarla");
	}

	private IntermittencyImportBatch loadForChange(UUID id, long version) {
		IntermittencyImportBatch batch = load(id);
		batch.requireVersion(version);
		return batch;
	}

	private IntermittencyImportRow loadRow(IntermittencyImportBatch batch, UUID rowId, long version) {
		IntermittencyImportRow row = imports.findRow(rowId)
				.filter(found -> found.getBatchId().equals(batch.getId()))
				.orElseThrow(() -> new NotFoundException("IMPORT_ROW_NOT_FOUND", "La fila no pertenece a este lote"));
		row.requireVersion(version);
		return row;
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

	private IntermittencyImportBatch load(UUID id) {
		return imports.findBatch(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}
}
