package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportRowRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
class EquipmentImportRowRepositoryAdapter implements EquipmentImportRowRepository {

	private final EquipmentImportRowJpaRepository jpa;

	@PersistenceContext
	private EntityManager entityManager;

	EquipmentImportRowRepositoryAdapter(EquipmentImportRowJpaRepository jpa) {
		this.jpa = jpa;
	}

	/** {@code persist} directo: con ID asignado, {@code save()} haria un SELECT por fila. */
	@Override
	public void insertAll(List<EquipmentImportRow> rows) {
		rows.forEach(entityManager::persist);
		entityManager.flush();
	}

	@Override
	public List<EquipmentImportRow> findByIds(UUID batchId, Collection<UUID> ids) {
		return jpa.findByBatchIdAndIdIn(batchId, ids);
	}

	@Override
	public PageResult<EquipmentImportRow> search(UUID batchId, EquipmentRowClassification classification,
			PageQuery pageQuery) {
		Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by("rowNumber"));
		Page<EquipmentImportRow> page = classification == null ? jpa.findByBatchId(batchId, pageable)
				: jpa.findByBatchIdAndClassification(batchId, classification, pageable);
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<EquipmentImportRow> nextToApply(UUID batchId, int limit) {
		return jpa.nextToApply(batchId, PageRequest.of(0, limit));
	}

	@Override
	public void markUnapprovedAsSkipped(UUID batchId) {
		jpa.markUnapprovedAsSkipped(batchId);
	}

	@Override
	public int countWritten(UUID batchId) {
		return Math.toIntExact(jpa.countWritten(batchId));
	}
}
