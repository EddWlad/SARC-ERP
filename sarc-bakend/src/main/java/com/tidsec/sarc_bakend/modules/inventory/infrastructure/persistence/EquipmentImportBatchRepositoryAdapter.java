package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentImportBatchRepository;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class EquipmentImportBatchRepositoryAdapter implements EquipmentImportBatchRepository {

	private final EquipmentImportBatchJpaRepository jpa;

	EquipmentImportBatchRepositoryAdapter(EquipmentImportBatchJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public EquipmentImportBatch save(EquipmentImportBatch batch) {
		return jpa.saveAndFlush(batch);
	}

	@Override
	public Optional<EquipmentImportBatch> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public PageResult<EquipmentImportBatch> search(PageQuery pageQuery) {
		Page<EquipmentImportBatch> page = jpa.findAll(PageRequest.of(pageQuery.page(), pageQuery.size(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<EquipmentImportBatch> findInProgress() {
		return jpa.findByStatusIn(List.of(ImportStatus.PARSING, ImportStatus.CONFIRMING));
	}
}
