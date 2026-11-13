package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.BlockingDay;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.repository.IntermittencyImportRepository;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class IntermittencyImportRepositoryAdapter implements IntermittencyImportRepository {

	/** Un punto con ese identificador nunca existe: relleno para el IN de una lista vacia. */
	private static final java.util.Collection<UUID> NO_POINTS = java.util.List.of(new UUID(0, 0));


	private static final int FLUSH_EVERY = 500;

	private final IntermittencyImportBatchJpaRepository batches;
	private final IntermittencyImportRowJpaRepository rows;

	@PersistenceContext
	private EntityManager entityManager;

	IntermittencyImportRepositoryAdapter(IntermittencyImportBatchJpaRepository batches,
			IntermittencyImportRowJpaRepository rows) {
		this.batches = batches;
		this.rows = rows;
	}

	@Override
	public IntermittencyImportBatch saveBatch(IntermittencyImportBatch batch) {
		return batches.saveAndFlush(batch);
	}

	@Override
	public Optional<IntermittencyImportBatch> findBatch(UUID id) {
		return batches.findById(id);
	}

	@Override
	public PageResult<IntermittencyImportBatch> searchBatches(PageQuery page) {
		return toResult(batches.findAll(PageRequest.of(page.page(), page.size(),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))));
	}

	@Override
	public List<IntermittencyImportBatch> findBatchesInProgress() {
		return batches.findByStatusIn(List.of(ImportStatus.PARSING, ImportStatus.CONFIRMING));
	}

	/** {@code persist} directo: con ID asignado, {@code save()} consultaria fila por fila. */
	@Override
	public void insertRows(List<IntermittencyImportRow> list) {
		for (int index = 0; index < list.size(); index++) {
			entityManager.persist(list.get(index));
			if ((index + 1) % FLUSH_EVERY == 0) {
				entityManager.flush();
			}
		}
		entityManager.flush();
	}

	@Override
	public IntermittencyImportRow saveRow(IntermittencyImportRow row) {
		return rows.saveAndFlush(row);
	}

	@Override
	public Optional<IntermittencyImportRow> findRow(UUID id) {
		return rows.findById(id);
	}

	@Override
	public List<IntermittencyImportRow> findRows(UUID batchId) {
		return rows.findByBatchIdOrderByRowNumber(batchId);
	}

	@Override
	public List<IntermittencyImportRow> findRows(UUID batchId, java.util.Collection<UUID> equipmentIds) {
		return equipmentIds.isEmpty() ? List.of() : rows.findByBatchIdAndEquipmentIdIn(batchId, equipmentIds);
	}

	@Override
	public com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowCounts countRows(UUID batchId) {
		int[] counts = new int[RowClassification.values().length];
		for (Object[] line : rows.countByClassification(batchId)) {
			counts[RowClassification.valueOf((String) line[0]).ordinal()] = ((Number) line[1]).intValue();
		}
		return new com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowCounts(
				(int) rows.countAll(batchId), (int) rows.countActiveWindows(batchId),
				counts[RowClassification.NEW.ordinal()], counts[RowClassification.IDENTICAL.ordinal()],
				counts[RowClassification.CONFLICT.ordinal()], counts[RowClassification.INVALID.ordinal()],
				counts[RowClassification.PENDING_LINK.ordinal()], counts[RowClassification.OUT_OF_SCOPE.ordinal()],
				counts[RowClassification.REVIEW.ordinal()], (int) rows.countSkipped(batchId));
	}

	@Override
	public PageResult<IntermittencyImportRow> searchRows(UUID batchId, RowClassification classification,
			PageQuery page) {
		PageRequest request = PageRequest.of(page.page(), page.size());
		return toResult(classification == null ? rows.findByBatchIdOrderByRowNumber(batchId, request)
				: rows.findByBatchIdAndClassificationOrderByRowNumber(batchId, classification, request));
	}

	@Override
	public PageResult<IntermittencyImportRow> searchPendingRows(LocalDate from, LocalDate to,
			RowClassification classification, java.util.Collection<UUID> equipmentIds, PageQuery page) {
		boolean allPoints = equipmentIds.isEmpty();
		return toResult(rows.searchPending(classification == null ? null : classification.name(), from, to,
				allPoints, allPoints ? NO_POINTS : equipmentIds, PageRequest.of(page.page(), page.size())));
	}

	@Override
	public List<BlockingDay> findBlockingDays(LocalDate from, LocalDate to) {
		List<BlockingDay> days = new java.util.ArrayList<>();
		for (Object[] row : rows.findBlocking(from, to)) {
			UUID equipmentId = row[1] == null ? null : row[1] instanceof UUID id ? id : UUID.fromString(row[1].toString());
			LocalDate day = row[2] instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) row[2];
			days.add(new BlockingDay(RowClassification.valueOf(row[0].toString()), equipmentId, day));
		}
		return days;
	}

	@Override
	public List<IntermittencyImportBatch> findUnconfirmedBatchesTouching(LocalDate day) {
		return batches.findReadyTouching(day);
	}

	@Override
	public java.util.Map<LocalDate, Integer> countUnconfirmedBatchesByDay(LocalDate from, LocalDate to) {
		java.util.Map<LocalDate, Integer> counts = new java.util.HashMap<>();
		for (Object[] row : rows.countUnconfirmedByDay(from, to)) {
			LocalDate day = row[0] instanceof java.sql.Date date ? date.toLocalDate() : (LocalDate) row[0];
			counts.put(day, ((Number) row[1]).intValue());
		}
		return counts;
	}

	@Override
	public long countPendingRows(LocalDate from, LocalDate to, RowClassification classification,
			java.util.Collection<UUID> equipmentIds) {
		boolean allPoints = equipmentIds.isEmpty();
		return rows.countPending(classification == null ? null : classification.name(), from, to, allPoints,
				allPoints ? NO_POINTS : equipmentIds);
	}

	private static <T> PageResult<T> toResult(Page<T> found) {
		return new PageResult<>(found.getContent(), found.getNumber(), found.getSize(), found.getTotalElements(),
				found.getTotalPages());
	}
}
