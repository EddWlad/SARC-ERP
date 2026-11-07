package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;

interface MeasurementJpaRepository extends JpaRepository<Measurement, UUID> {
    List<Measurement> findByConnectorIdAndSourceAndTemporalPrecision(UUID connectorId,MeasurementSource source,TemporalPrecision precision);

	String SEARCH = """
			FROM upt_measurement m
			JOIN upt_measurement_revision r ON r.measurement_id = m.id AND r.revision = m.latest_revision
			WHERE (CAST(:connectorId AS uuid) IS NULL OR m.connector_id = :connectorId)
			  AND (CAST(:equipmentId AS uuid) IS NULL OR m.equipment_id = :equipmentId)
			  AND (NOT :onlyEffective OR (m.effective_revision IS NOT NULL AND NOT m.excluded AND m.period_precision <> 'REQUIRES_RECONCILIATION'))
			  AND (CAST(:queue AS text) IS NULL
			    OR (:queue = 'TEMPORAL_RECONCILIATION' AND m.period_precision = 'REQUIRES_RECONCILIATION')
			    OR (:queue = 'EXCLUDED' AND m.excluded)
			    OR (:queue = 'VALIDATED' AND NOT m.excluded AND m.effective_revision IS NOT NULL)
			    OR (:queue = 'PENDING' AND NOT m.excluded AND r.review_state = 'PENDING'
			        AND jsonb_array_length(r.observations) = 0)
			    OR (:queue = 'OBSERVED' AND NOT m.excluded AND r.review_state = 'PENDING'
			        AND jsonb_array_length(r.observations) > 0))
			""";

	@Query(value = "SELECT m.* " + SEARCH + " ORDER BY m.period_end DESC, m.id",
			countQuery = "SELECT count(*) " + SEARCH, nativeQuery = true)
	Page<Measurement> search(@Param("connectorId") UUID connectorId, @Param("equipmentId") UUID equipmentId,
			@Param("queue") String queue, @Param("onlyEffective") boolean onlyEffective, Pageable pageable);

	/** Valores de la vigente o, sin ella, de la ultima. */
@Query(value = "SELECT max(validated_at) FROM upt_measurement_revision", nativeQuery = true)
	Instant lastValidatedAt();

	@Query(value = """
			SELECT count(*) FROM upt_import_row
			WHERE outcome = 'PENDING_LINK' AND period_precision = 'KNOWN_RANGE'
			  AND (CAST(:from AS timestamptz) IS NULL OR period_end BETWEEN CAST(:from AS timestamptz) AND CAST(:to AS timestamptz))
			""", nativeQuery = true)
	long countPendingLinks(@Param("from") Instant from, @Param("to") Instant to);

	Optional<Measurement> findByConnectorIdAndSourceAndPeriodStartAndPeriodEndAndTemporalPrecision(UUID connectorId,
			MeasurementSource source, Instant periodStart, Instant periodEnd, TemporalPrecision precision);

	List<Measurement> findByConnectorIdInAndSourceAndPeriodStartAndPeriodEndAndTemporalPrecision(Collection<UUID> connectorIds,
			MeasurementSource source, Instant periodStart, Instant periodEnd, TemporalPrecision precision);
    Optional<Measurement> findByConnectorIdAndSourceAndReferenceWeekAndTemporalPrecision(
        UUID connectorId,MeasurementSource source,LocalDate referenceWeek,TemporalPrecision precision);
    List<Measurement> findByConnectorIdInAndSourceAndReferenceWeekAndTemporalPrecisionOrderByPeriodEndDescPeriodStartDesc(
        Collection<UUID> ids,MeasurementSource source,LocalDate week,TemporalPrecision precision);
}
