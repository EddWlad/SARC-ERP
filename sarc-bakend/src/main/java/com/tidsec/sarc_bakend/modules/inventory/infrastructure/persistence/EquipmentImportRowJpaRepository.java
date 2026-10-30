package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;

interface EquipmentImportRowJpaRepository extends JpaRepository<EquipmentImportRow, UUID> {

	List<EquipmentImportRow> findByBatchIdAndIdIn(UUID batchId, Collection<UUID> ids);

	Page<EquipmentImportRow> findByBatchId(UUID batchId, Pageable pageable);

	Page<EquipmentImportRow> findByBatchIdAndClassification(UUID batchId, EquipmentRowClassification classification,
			Pageable pageable);

	/** Misma regla que {@code EquipmentImportRow.isApplicable()}, expresada en la consulta. */
	@Query("""
			select r from EquipmentImportRow r
			where r.batchId = :batchId and r.outcome is null
			  and (r.classification = com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification.NEW
			       or r.classification = com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification.IDENTITY_COMPLETED
			       or (r.classification = com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification.MODIFIED
			           and r.approved = true))
			order by r.rowNumber""")
	List<EquipmentImportRow> nextToApply(@Param("batchId") UUID batchId, Pageable pageable);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update EquipmentImportRow r
			set r.outcome = com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome.SKIPPED,
			    r.outcomeDetail = 'Sin aprobar'
			where r.batchId = :batchId and r.outcome is null
			  and r.classification = com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification.MODIFIED""")
	void markUnapprovedAsSkipped(@Param("batchId") UUID batchId);

	@Query("""
			select count(r) from EquipmentImportRow r
			where r.batchId = :batchId
			  and r.outcome in (com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome.CREATED,
			                    com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome.UPDATED,
			                    com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome.IDENTITY_ADDED)""")
	long countWritten(@Param("batchId") UUID batchId);
}
