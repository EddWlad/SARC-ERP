package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.ImportRowRepository;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
class ImportRowRepositoryAdapter implements ImportRowRepository {

	private static final List<RowClassification> WRITING = Arrays.stream(RowClassification.values())
			.filter(RowClassification::writesSession).toList();

	private static final int FLUSH_EVERY = 1_000;

	private final ImportRowJpaRepository jpa;

	@PersistenceContext
	private EntityManager entityManager;

	ImportRowRepositoryAdapter(ImportRowJpaRepository jpa) {
		this.jpa = jpa;
	}

	/**
	 * {@code persist} directo: con ID asignado, {@code save()} haria un SELECT por fila para decidir
	 * entre insertar o fusionar. Se vacia por tramos para aprovechar los lotes JDBC y no acumular
	 * miles de entidades en memoria.
	 */
	@Override
	public void insertAll(List<ImportRow> rows) {
		for (int index = 0; index < rows.size(); index++) {
			entityManager.persist(rows.get(index));
			if ((index + 1) % FLUSH_EVERY == 0) {
				entityManager.flush();
				entityManager.clear();
			}
		}
		entityManager.flush();
	}

	@Override
	public List<ImportRow> findByIds(UUID batchId, Collection<UUID> ids) {
		return jpa.findByBatchIdAndIdIn(batchId, ids);
	}

	@Override
	public PageResult<ImportRow> search(UUID batchId, RowClassification classification, PageQuery pageQuery) {
		Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by("rowNumber"));
		Page<ImportRow> page = classification == null ? jpa.findByBatchId(batchId, pageable)
				: jpa.findByBatchIdAndClassification(batchId, classification, pageable);
		return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

	@Override
	public List<ImportRow> nextToApply(UUID batchId, int limit) {
		return jpa.nextToApply(batchId, WRITING, PageRequest.of(0, limit));
	}

	@Override
	public void markUnapprovedAsSkipped(UUID batchId) {
		jpa.markSkipped(batchId, WRITING);
	}

	@Override
	public int countWritten(UUID batchId) {
		return Math.toIntExact(jpa.countWritten(batchId));
	}
}
