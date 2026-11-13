package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.ImportFormat;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowCounts;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowEdit;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ImportProvenance;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowChange;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowSource;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.RowResolution;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.WindowRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportFailure;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Trabajo en segundo plano de un lote: la interpretacion inicial del archivo y, al confirmar, la creacion de las
 * ventanas. Cada tramo de filas se confirma en su propia transaccion y cada ventana guarda su procedencia (lote,
 * fila y par), asi que reintentar una confirmacion interrumpida continua donde quedo sin repetir nada.
 */
@Service
class IntermittencyImportProcessor {

	private static final Logger log = LoggerFactory.getLogger(IntermittencyImportProcessor.class);
	static final int CHUNK_SIZE = 50;
	static final String REPLACE_JUSTIFICATION = "Reemplazada por la importacion del lote";

	private final IntermittencyImportRepository imports;
	private final WindowRepository windows;
	private final ImportClassifier classifier;
	private final WindowWriteGuard guard;
	private final KiaPointDirectory points;
	private final AuditTrail audit;
	private final TransactionTemplate transactions;
	private final CurrentActor actor;
	private final Clock clock;

	IntermittencyImportProcessor(IntermittencyImportRepository imports, WindowRepository windows,
			ImportClassifier classifier, WindowWriteGuard guard, KiaPointDirectory points, AuditTrail audit,
			TransactionTemplate transactions, CurrentActor actor, Clock clock) {
		this.imports = imports;
		this.windows = windows;
		this.classifier = classifier;
		this.guard = guard;
		this.points = points;
		this.audit = audit;
		this.transactions = transactions;
		this.actor = actor;
		this.clock = clock;
	}

	// ----- Interpretacion inicial -----

	void prepare(UUID batchId, Supplier<List<RawRow>> parsed) {
		try {
			List<RawRow> raws = parsed.get();
			transactions.executeWithoutResult(status -> createPreview(batchId, raws));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	private void createPreview(UUID batchId, List<RawRow> raws) {
		IntermittencyImportBatch batch = load(batchId);
		List<IntermittencyImportRow> rows = raws.stream().map(raw -> IntermittencyImportRow.read(batchId, raw))
				.toList();
		classifier.classify(rows);
		imports.insertRows(rows);
		LocalDate earliest = null;
		LocalDate latest = null;
		for (IntermittencyImportRow row : rows) {
			for (LocalDate day : row.getTouchedDays()) {
				earliest = earliest == null || day.isBefore(earliest) ? day : earliest;
				latest = latest == null || day.isAfter(latest) ? day : latest;
			}
		}
		RowCounts counts = RowCounts.of(rows);
		batch.previewReady(counts, earliest, latest);
		imports.saveBatch(batch);
		audit.record("INTERMITTENCY_IMPORT_PREVIEW_READY", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("rows", String.valueOf(counts.total()), "windows", String.valueOf(counts.windows()), "new",
						String.valueOf(counts.newRows()), "conflict", String.valueOf(counts.conflict()), "invalid",
						String.valueOf(counts.invalid()), "pendingLink", String.valueOf(counts.pendingLink())));
	}

	// ----- Confirmacion -----

	void apply(UUID batchId) {
		try {
			List<UUID> pending = transactions.execute(status -> rowsToApply(batchId));
			for (int from = 0; from < pending.size(); from += CHUNK_SIZE) {
				List<UUID> chunk = pending.subList(from, Math.min(pending.size(), from + CHUNK_SIZE));
				transactions.executeWithoutResult(status -> applyChunk(batchId, chunk));
			}
			transactions.executeWithoutResult(status -> complete(batchId, pending.size()));
		}
		catch (RuntimeException ex) {
			fail(batchId, ex);
		}
	}

	private List<UUID> rowsToApply(UUID batchId) {
		return imports.findRows(batchId).stream()
				.filter(row -> RowResolution.appliesOnConfirm(row.getDecision()))
				.filter(row -> row.getEquipmentId() != null)
				.filter(row -> !row.getInterpretation().active().isEmpty())
				.map(IntermittencyImportRow::getId).toList();
	}

	private void applyChunk(UUID batchId, List<UUID> rowIds) {
		IntermittencyImportBatch batch = load(batchId);
		Set<String> done = windows.findProvenance(batchId);
		for (UUID id : rowIds) {
			IntermittencyImportRow row = imports.findRow(id)
					.orElseThrow(() -> new NotFoundException("IMPORT_ROW_NOT_FOUND", "La fila no existe"));
			applyRow(batch, row, done, batch.getConfirmedBy() != null ? batch.getConfirmedBy() : actor.id().orElse(null));
		}
	}

	/** Crea las ventanas de una fila resuelta despues de confirmar el lote, dentro de la transaccion en curso. */
	void applyResolved(IntermittencyImportBatch batch, IntermittencyImportRow row, UUID author) {
		applyRow(batch, row, windows.findProvenance(batch.getId(), row.getRowNumber()), author);
	}

	private void applyRow(IntermittencyImportBatch batch, IntermittencyImportRow row, Set<String> done,
			UUID author) {
		UUID point = row.getEquipmentId();
		guard.lock(List.of(point));
		KiaPointReference reference = guard.requireWritable(point);
		RowInterpretation interpretation = row.getInterpretation();
		Instant now = Instant.now(clock);
		List<RowWindow> ordered = new ArrayList<>(interpretation.active());
		ordered.sort(Comparator.comparingInt(RowWindow::pair));
		int created = 0;
		for (RowWindow window : ordered) {
			String key = row.getRowNumber() + ":" + window.pair();
			if (done.contains(key) || window.has(IssueCode.ALREADY_RECORDED) || !window.hasShape()) {
				continue;
			}
			if (window.decision() == WindowResolution.REPLACE) {
				replace(row, window, author, now);
			}
			else {
				create(batch, row, window, reference, author, now);
			}
			done.add(key);
			created++;
		}
		audit.record("INTERMITTENCY_IMPORT_ROW_APPLIED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batch.getId(),
				Map.of("row", String.valueOf(row.getRowNumber()), "windows", String.valueOf(created)));
	}

	private void create(IntermittencyImportBatch batch, IntermittencyImportRow row, RowWindow window,
			KiaPointReference point, UUID author, Instant now) {
		guard.requireFree(point.id(), window.startInstant(), window.endInstant(), null);
		IntermittencyWindow created = IntermittencyWindow.register(point.id(), WindowService.label(point),
				window.startInstant(), window.endInstant(), emptyToNull(row.getInterpretation().comment()),
				source(batch.getFormat()), new ImportProvenance(batch.getId(), row.getRowNumber(), window.pair()),
				window.test(), now);
		IntermittencyWindow saved = guard.save(created);
		windows.saveRevision(saved.snapshot(WindowChange.CREATED, null, author, now));
		audit.record("INTERMITTENCY_WINDOW_CREATED", IntermittencyWindow.AUDIT_ENTITY_TYPE, saved.getId(),
				Map.of("equipmentId", point.id().toString(), "state", saved.getState().name(), "source",
						saved.getSource().name(), "batch", batch.getId().toString()));
	}

	/** Reemplaza la ventana ya guardada con una revision justificada (FR-029). */
	private void replace(IntermittencyImportRow row, RowWindow window, UUID author, Instant now) {
		IntermittencyWindow existing = windows.findById(UUID.fromString(window.conflictWindowId())).orElseThrow(
				() -> new NotFoundException("WINDOW_NOT_FOUND", "La ventana a reemplazar ya no existe"));
		guard.requireFree(existing.getEquipmentId(), window.startInstant(), window.endInstant(), existing.getId());
		String justification = justification(row, window.pair());
		var revision = existing.replaceByImport(window.startInstant(), window.endInstant(), justification, author,
				now);
		guard.save(existing);
		windows.saveRevision(revision);
		audit.record("INTERMITTENCY_WINDOW_REPLACED", IntermittencyWindow.AUDIT_ENTITY_TYPE, existing.getId(),
				Map.of("equipmentId", existing.getEquipmentId().toString(), "batch", row.getBatchId().toString()));
	}

	/** La justificacion que la persona dio al elegir REPLACE para ese par, guardada entre las ediciones. */
	static String justification(IntermittencyImportRow row, int pair) {
		List<RowEdit> edits = row.getEdits();
		for (int index = edits.size() - 1; index >= 0; index--) {
			RowEdit edit = edits.get(index);
			if (RowEdit.DECISION.equals(edit.target()) && edit.pair() != null
					&& edit.pair() == pair && WindowResolution.REPLACE.name().equals(edit.field())
					&& edit.value() != null && !edit.value().isBlank()) {
				return edit.value();
			}
		}
		return REPLACE_JUSTIFICATION;
	}

	private void complete(UUID batchId, int applied) {
		IntermittencyImportBatch batch = load(batchId);
		batch.complete(applied);
		imports.saveBatch(batch);
		audit.record("INTERMITTENCY_IMPORT_COMPLETED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batchId,
				Map.of("rows", String.valueOf(applied)));
	}

	private void fail(UUID batchId, RuntimeException ex) {
		ImportFailure failure = ImportFailure.of(ex);
		if (failure.unexpected()) {
			log.error("Fallo el procesamiento del lote de intermitencias {}", batchId, ex);
		}
		transactions.executeWithoutResult(status -> {
			IntermittencyImportBatch batch = load(batchId);
			if (batch.isInProgress()) {
				batch.fail(failure.code(), failure.message());
				imports.saveBatch(batch);
				audit.record("INTERMITTENCY_IMPORT_FAILED", IntermittencyImportBatch.AUDIT_ENTITY_TYPE, batchId,
						Map.of("code", failure.code()));
			}
		});
	}

	private static WindowSource source(ImportFormat format) {
		return switch (format) {
			case XLSX -> WindowSource.XLSX;
			case CSV -> WindowSource.CSV;
			case PASTE -> WindowSource.PASTE;
		};
	}

	private static String emptyToNull(String text) {
		return text == null || text.isBlank() ? null : text;
	}

	private IntermittencyImportBatch load(UUID id) {
		return imports.findBatch(id)
				.orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "El lote de importacion no existe"));
	}

	static BusinessRuleException unresolved(int count, List<Integer> sample) {
		return new BusinessRuleException("IMPORT_UNRESOLVED_ROWS",
				"Hay " + count + " filas sin resolver; corrijalas, decida o omitalas antes de confirmar",
				Map.of("unresolvedRows", count, "sampleRows", sample));
	}
}
