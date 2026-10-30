package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;

interface ImportRowJpaRepository extends JpaRepository<ImportRow, UUID> {

	List<ImportRow> findByBatchIdAndIdIn(UUID batchId, Collection<UUID> ids);

	Page<ImportRow> findByBatchId(UUID batchId, Pageable pageable);

	Page<ImportRow> findByBatchIdAndClassification(UUID batchId, RowClassification classification, Pageable pageable);

	/** Misma regla que {@code ImportRow.isApplicable()}, expresada en la consulta. */
	@Query("""
			select r from ImportRow r
			where r.batchId = :batchId and r.outcome is null
			  and r.classification in :writing
			  and (r.approved = true
			       or (r.classification <> com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification.MODIFIED
			           and r.nonPreferredSource = false))
			  and (r.classification <> com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification.EQUIPMENT_CHANGED
			       or r.reassigned = true)
			order by r.rowNumber""")
	List<ImportRow> nextToApply(@Param("batchId") UUID batchId,
			@Param("writing") Collection<RowClassification> writing, Pageable pageable);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update ImportRow r
			set r.outcome = com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome.SKIPPED,
			    r.outcomeDetail = 'Sin aprobar o sin reasignar'
			where r.batchId = :batchId and r.outcome is null and r.classification in :writing""")
	void markSkipped(@Param("batchId") UUID batchId, @Param("writing") Collection<RowClassification> writing);

	@Query("""
			select count(r) from ImportRow r
			where r.batchId = :batchId
			  and r.outcome in (com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome.INSERTED,
			                    com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome.CORRECTED,
			                    com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome.REASSIGNED,
			                    com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome.LINKED)""")
	long countWritten(@Param("batchId") UUID batchId);
}
