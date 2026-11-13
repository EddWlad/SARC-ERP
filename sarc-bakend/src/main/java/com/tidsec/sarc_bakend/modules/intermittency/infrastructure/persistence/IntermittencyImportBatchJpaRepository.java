package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

interface IntermittencyImportBatchJpaRepository extends JpaRepository<IntermittencyImportBatch, UUID> {

	List<IntermittencyImportBatch> findByStatusIn(Collection<ImportStatus> statuses);

	/** Lotes en vista previa con alguna fila que toca el dia, el mas antiguo primero. */
	@Query(value = """
			SELECT b.* FROM itm_import_batch b
			WHERE b.status = 'READY' AND EXISTS (SELECT 1 FROM itm_import_row r
			    WHERE r.batch_id = b.id AND CAST(:day AS date) = ANY (r.touched_days))
			ORDER BY b.created_at
			""", nativeQuery = true)
	List<IntermittencyImportBatch> findReadyTouching(@Param("day") LocalDate day);
}
