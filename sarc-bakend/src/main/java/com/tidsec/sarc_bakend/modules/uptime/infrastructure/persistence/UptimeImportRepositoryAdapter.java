package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowOutcome;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.UptimeImportRepository;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
class UptimeImportRepositoryAdapter implements UptimeImportRepository {

	private static final int FLUSH_EVERY = 1_000;

	private final UptimeImportBatchJpaRepository batches;
	private final UptimeImportRowJpaRepository rows;
	private final ConnectorAliasJpaRepository aliases;

	@PersistenceContext
	private EntityManager entityManager;

	UptimeImportRepositoryAdapter(UptimeImportBatchJpaRepository batches, UptimeImportRowJpaRepository rows,
			ConnectorAliasJpaRepository aliases) {
		this.batches = batches;
		this.rows = rows;
		this.aliases = aliases;
	}

    @Override
    public Optional<Original> findOriginal(UUID batchId) {
        var values=entityManager.createNativeQuery("SELECT original_ref,checksum FROM upt_import_batch WHERE id=?",Object[].class)
            .setParameter(1,batchId).getResultList();
        if(values.isEmpty())return Optional.empty();
        Object[] value=(Object[])values.getFirst();return Optional.of(new Original((String)value[0],(String)value[1]));
    }
    @Override
    public List<UptimeImportRow> findProvenanceRows(UUID measurementId,UUID batchId) {
        return rows.findByAppliedMeasurementIdAndBatchId(measurementId,batchId);
    }

	@Override
	public UptimeImportBatch saveBatch(UptimeImportBatch batch) {
		return batches.saveAndFlush(batch);
	}

	@Override
	public Optional<UptimeImportBatch> findBatch(UUID id) {
		return batches.findById(id);
	}

	@Override
	public PageResult<UptimeImportBatch> searchBatches(PageQuery page) {
		return result(batches.findAll(PageRequest.of(page.page(), page.size(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")))));
	}

	@Override
	public List<UptimeImportBatch> findBatchesInProgress() {
		return batches.findByStatusIn(List.of(ImportStatus.PARSING, ImportStatus.CONFIRMING));
	}

	/** {@code persist} directo: con ID asignado, {@code save()} consultaria fila por fila. */
	@Override
	public void insertRows(List<UptimeImportRow> list) {
		for (int index = 0; index < list.size(); index++) {
			entityManager.persist(list.get(index));
			if ((index + 1) % FLUSH_EVERY == 0) {
				entityManager.flush();
				entityManager.clear();
			}
		}
		entityManager.flush();
	}

	@Override
	public Optional<UptimeImportRow> findRow(UUID id) {
		return rows.findById(id);
	}

	@Override
	public List<UptimeImportRow> findRows(UUID batchId, Collection<UUID> ids) {
		return rows.findByBatchIdAndIdIn(batchId, ids);
	}

	@Override
	public PageResult<UptimeImportRow> searchRows(UUID batchId, UptimeRowClassification classification,
			PageQuery page) {
		PageRequest request = PageRequest.of(page.page(), page.size(), Sort.by("rowNumber"));
		return result(classification == null ? rows.findByBatchId(batchId, request)
				: rows.findByBatchIdAndClassification(batchId, classification, request));
	}

	@Override
	public List<UptimeImportRow> nextToApply(UUID batchId, int limit) {
		return rows.findByBatchIdAndOutcomeIsNull(batchId, PageRequest.of(0, limit, Sort.by("rowNumber")));
	}

	@Override
	public int countApplied(UUID batchId) {
		return Math.toIntExact(rows.countByBatchIdAndOutcome(batchId, UptimeRowOutcome.APPLIED));
	}

	@Override
	public PageResult<UptimeImportRow> searchPendingLinks(PageQuery page) {
		return result(rows.findByOutcome(UptimeRowOutcome.PENDING_LINK, PageRequest.of(page.page(), page.size(),
				Sort.by(Sort.Direction.DESC, "periodEnd").and(Sort.by("rowNumber")).and(Sort.by("id")))));
	}

	@Override
	public List<ConnectorAlias> findAliases(MeasurementSource source, Collection<UUID> equipmentIds) {
		return equipmentIds.isEmpty() ? List.of() : aliases.findBySourceAndEquipmentIdIn(source, equipmentIds);
	}

	@Override
	public Optional<ConnectorAlias> findAlias(UUID id) {
		return aliases.findById(id);
	}

	@Override
	public Optional<ConnectorAlias> findAlias(MeasurementSource source, UUID equipmentId, String labelKey) {
		return aliases.findBySourceAndEquipmentIdAndLabelKey(source, equipmentId, labelKey);
	}

	@Override
	public ConnectorAlias saveAlias(ConnectorAlias alias) {
		return aliases.saveAndFlush(alias);
	}

	@Override
	public PageResult<ConnectorAlias> searchAliases(UUID equipmentId, PageQuery page) {
		PageRequest request = PageRequest.of(page.page(), page.size(), Sort.by("labelOriginal").and(Sort.by("id")));
		return result(equipmentId == null ? aliases.findAll(request) : aliases.findByEquipmentId(equipmentId, request));
	}

	private static <T> PageResult<T> result(Page<T> page) {
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}
    @Override public boolean hasRowsWithoutKnownRange(UUID batchId) {
        return rows.existsByBatchIdAndTemporalPrecisionNot(batchId,
            com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision.KNOWN_RANGE);
    }
}
