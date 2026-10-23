package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportBatchRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@Component
class ImportBatchRepositoryAdapter implements ImportBatchRepository {

	private final ImportBatchJpaRepository jpa;

	ImportBatchRepositoryAdapter(ImportBatchJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public ImportBatch save(ImportBatch batch) {
		return jpa.saveAndFlush(batch);
	}

	@Override
	public Optional<ImportBatch> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public PageResult<ImportBatch> search(PageQuery pageQuery) {
		Page<ImportBatch> page = jpa.findAll(PageRequest.of(pageQuery.page(), pageQuery.size(),
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<ImportBatch> findInProgress() {
		return jpa.findByStatusIn(List.of(ImportStatus.PARSING, ImportStatus.CONFIRMING));
	}
}
