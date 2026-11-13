package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;

interface IntermittencyImportRowJpaRepository extends JpaRepository<IntermittencyImportRow, UUID> {

	List<IntermittencyImportRow> findByBatchIdOrderByRowNumber(UUID batchId);

	Page<IntermittencyImportRow> findByBatchIdOrderByRowNumber(UUID batchId, Pageable pageable);

	Page<IntermittencyImportRow> findByBatchIdAndClassificationOrderByRowNumber(UUID batchId,
			RowClassification classification, Pageable pageable);

	List<IntermittencyImportRow> findByBatchIdAndEquipmentIdIn(UUID batchId, java.util.Collection<UUID> equipmentIds);

	@Query(value = """
			SELECT r.classification, count(*) FROM itm_import_row r
			WHERE r.batch_id = :batchId AND r.decision IS DISTINCT FROM 'SKIP' GROUP BY r.classification
			""", nativeQuery = true)
	List<Object[]> countByClassification(@Param("batchId") UUID batchId);

	@Query(value = "SELECT count(*) FROM itm_import_row r WHERE r.batch_id = :batchId AND r.decision = 'SKIP'",
			nativeQuery = true)
	long countSkipped(@Param("batchId") UUID batchId);

	@Query(value = "SELECT count(*) FROM itm_import_row r WHERE r.batch_id = :batchId", nativeQuery = true)
	long countAll(@Param("batchId") UUID batchId);

	/** Ventanas activas: las que no se omitieron, no conservan la ya registrada ni se fusionaron en otra. */
	@Query(value = """
			SELECT coalesce(sum((SELECT count(*) FROM jsonb_array_elements(r.interpretation -> 'windows') w
			    WHERE coalesce(w ->> 'decision', '') NOT IN ('OMIT', 'KEEP_EXISTING')
			      AND (w ->> 'mergedIntoPair') IS NULL)), 0)
			FROM itm_import_row r WHERE r.batch_id = :batchId AND r.decision IS DISTINCT FROM 'SKIP'
			""", nativeQuery = true)
	long countActiveWindows(@Param("batchId") UUID batchId);

	@Query(value = """
			SELECT r.* FROM itm_import_row r JOIN itm_import_batch b ON b.id = r.batch_id
			WHERE b.status = 'COMPLETED' AND r.decision = 'KEEP_PENDING' AND r.resolved_at IS NULL
			  AND (CAST(:classification AS text) IS NULL OR r.classification = CAST(:classification AS text))
			  AND ((CAST(:from AS date) IS NULL AND CAST(:to AS date) IS NULL) OR EXISTS (SELECT 1 FROM unnest(r.touched_days) d
			       WHERE (CAST(:from AS date) IS NULL OR d >= CAST(:from AS date))
			         AND (CAST(:to AS date) IS NULL OR d <= CAST(:to AS date))))
			  AND (:allPoints = TRUE OR r.equipment_id IN (:equipmentIds))
			ORDER BY b.created_at DESC, r.row_number
			""", countQuery = """
			SELECT count(*) FROM itm_import_row r JOIN itm_import_batch b ON b.id = r.batch_id
			WHERE b.status = 'COMPLETED' AND r.decision = 'KEEP_PENDING' AND r.resolved_at IS NULL
			  AND (CAST(:classification AS text) IS NULL OR r.classification = CAST(:classification AS text))
			  AND ((CAST(:from AS date) IS NULL AND CAST(:to AS date) IS NULL) OR EXISTS (SELECT 1 FROM unnest(r.touched_days) d
			       WHERE (CAST(:from AS date) IS NULL OR d >= CAST(:from AS date))
			         AND (CAST(:to AS date) IS NULL OR d <= CAST(:to AS date))))
			  AND (:allPoints = TRUE OR r.equipment_id IN (:equipmentIds))
			""", nativeQuery = true)
	Page<IntermittencyImportRow> searchPending(@Param("classification") String classification,
			@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("allPoints") boolean allPoints,
			@Param("equipmentIds") java.util.Collection<UUID> equipmentIds, Pageable pageable);

	/** Un dia por cada dia de las pendientes por vincular o en conflicto dentro de {@code [from, to]}: dejan el dia sin cubrir. */
	@Query(value = """
			SELECT r.classification, r.equipment_id, d.day
			FROM itm_import_row r JOIN itm_import_batch b ON b.id = r.batch_id
			CROSS JOIN LATERAL unnest(r.touched_days) AS d(day)
			WHERE b.status = 'COMPLETED' AND r.decision = 'KEEP_PENDING' AND r.resolved_at IS NULL
			  AND r.classification IN ('PENDING_LINK', 'CONFLICT')
			  AND d.day >= CAST(:from AS date) AND d.day <= CAST(:to AS date)
			""", nativeQuery = true)
	List<Object[]> findBlocking(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Lotes en vista previa por dia: {@code [dia, cantidad]} para cada dia de {@code [from, to]} que alguno toca. */
	@Query(value = """
			SELECT d.day, count(DISTINCT r.batch_id)
			FROM itm_import_row r JOIN itm_import_batch b ON b.id = r.batch_id
			CROSS JOIN LATERAL unnest(r.touched_days) AS d(day)
			WHERE b.status = 'READY' AND d.day >= CAST(:from AS date) AND d.day <= CAST(:to AS date)
			GROUP BY d.day
			""", nativeQuery = true)
	List<Object[]> countUnconfirmedByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Cuenta las pendientes sin cargarlas: el dashboard solo necesita las cantidades. */
	@Query(value = """
			SELECT count(*) FROM itm_import_row r JOIN itm_import_batch b ON b.id = r.batch_id
			WHERE b.status = 'COMPLETED' AND r.decision = 'KEEP_PENDING' AND r.resolved_at IS NULL
			  AND (CAST(:classification AS text) IS NULL OR r.classification = CAST(:classification AS text))
			  AND ((CAST(:from AS date) IS NULL AND CAST(:to AS date) IS NULL) OR EXISTS (SELECT 1 FROM unnest(r.touched_days) d
			       WHERE (CAST(:from AS date) IS NULL OR d >= CAST(:from AS date))
			         AND (CAST(:to AS date) IS NULL OR d <= CAST(:to AS date))))
			  AND (:allPoints = TRUE OR r.equipment_id IN (:equipmentIds))
			""", nativeQuery = true)
	long countPending(@Param("classification") String classification, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("allPoints") boolean allPoints,
			@Param("equipmentIds") java.util.Collection<UUID> equipmentIds);
}
