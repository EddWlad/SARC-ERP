package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;

interface WindowJpaRepository extends JpaRepository<IntermittencyWindow, UUID> {

	@Query(value = """
			SELECT * FROM itm_window w
			WHERE w.equipment_id = :equipmentId AND w.state <> 'ANNULLED'
			  AND (CAST(:excludeId AS uuid) IS NULL OR w.id <> CAST(:excludeId AS uuid))
			  AND w.start_at < coalesce(CAST(:endAt AS timestamptz), 'infinity'::timestamptz)
			  AND coalesce(w.end_at, 'infinity'::timestamptz) > CAST(:startAt AS timestamptz)
			ORDER BY w.start_at
			""", nativeQuery = true)
	List<IntermittencyWindow> findOverlapping(@Param("equipmentId") UUID equipmentId,
			@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, @Param("excludeId") UUID excludeId);

	@Query(value = """
			SELECT * FROM itm_window w
			WHERE w.equipment_id = :equipmentId AND w.state <> 'ANNULLED'
			  AND (CAST(:excludeId AS uuid) IS NULL OR w.id <> CAST(:excludeId AS uuid))
			  AND w.start_at = CAST(:startAt AS timestamptz)
			  AND w.end_at IS NOT DISTINCT FROM CAST(:endAt AS timestamptz)
			""", nativeQuery = true)
	List<IntermittencyWindow> findIdentical(@Param("equipmentId") UUID equipmentId,
			@Param("startAt") Instant startAt, @Param("endAt") Instant endAt, @Param("excludeId") UUID excludeId);

	@Query("""
			SELECT w FROM IntermittencyWindow w
			WHERE w.startAt < :to AND coalesce(w.endAt, :cutOff) > :from
			  AND (:allPoints = TRUE OR w.equipmentId IN :equipmentIds)
			  AND (:allStates = TRUE OR w.state IN :states)
			ORDER BY w.startAt, w.id
			""")
	List<IntermittencyWindow> findInRange(@Param("from") Instant from, @Param("to") Instant to,
			@Param("cutOff") Instant cutOff, @Param("allPoints") boolean allPoints,
			@Param("equipmentIds") Collection<UUID> equipmentIds, @Param("allStates") boolean allStates,
			@Param("states") Collection<WindowState> states);

	@Query(value = """
			SELECT w FROM IntermittencyWindow w
			WHERE (:hasTo = FALSE OR w.startAt < :to)
			  AND (:hasFrom = FALSE OR w.endAt IS NULL OR w.endAt > :from)
			  AND (:allPoints = TRUE OR w.equipmentId IN :equipmentIds)
			  AND (:allStates = TRUE OR w.state IN :states)
			""", countQuery = """
			SELECT count(w) FROM IntermittencyWindow w
			WHERE (:hasTo = FALSE OR w.startAt < :to)
			  AND (:hasFrom = FALSE OR w.endAt IS NULL OR w.endAt > :from)
			  AND (:allPoints = TRUE OR w.equipmentId IN :equipmentIds)
			  AND (:allStates = TRUE OR w.state IN :states)
			""")
	Page<IntermittencyWindow> search(@Param("hasFrom") boolean hasFrom, @Param("from") Instant from,
			@Param("hasTo") boolean hasTo, @Param("to") Instant to, @Param("allPoints") boolean allPoints,
			@Param("equipmentIds") Collection<UUID> equipmentIds, @Param("allStates") boolean allStates,
			@Param("states") Collection<WindowState> states, Pageable pageable);

	@Query(value = "SELECT w.row_number || ':' || w.pair_number FROM itm_window w WHERE w.batch_id = :batchId", nativeQuery = true)
	List<String> findProvenance(@Param("batchId") UUID batchId);

	@Query(value = "SELECT w.row_number || ':' || w.pair_number FROM itm_window w WHERE w.batch_id = :batchId AND w.row_number = :rowNumber", nativeQuery = true)
	List<String> findRowProvenance(@Param("batchId") UUID batchId, @Param("rowNumber") int rowNumber);

	/** Solo el punto: no carga la entidad, para poder bloquear el punto antes de leerla. */
	@Query("SELECT w.equipmentId FROM IntermittencyWindow w WHERE w.id = :id")
	java.util.Optional<UUID> findEquipmentId(@Param("id") UUID id);

	List<IntermittencyWindow> findByStateOrderByStartAtAscIdAsc(WindowState state);
}
