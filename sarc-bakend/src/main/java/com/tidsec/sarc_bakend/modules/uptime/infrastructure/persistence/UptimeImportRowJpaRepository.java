package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowOutcome;

interface UptimeImportRowJpaRepository extends JpaRepository<UptimeImportRow, UUID> {
    List<UptimeImportRow> findByAppliedMeasurementIdAndBatchId(UUID measurementId,UUID batchId);

	List<UptimeImportRow> findByBatchIdAndIdIn(UUID batchId, Collection<UUID> ids);

	Page<UptimeImportRow> findByBatchId(UUID batchId, Pageable pageable);

	Page<UptimeImportRow> findByBatchIdAndClassification(UUID batchId, UptimeRowClassification classification,
			Pageable pageable);

	List<UptimeImportRow> findByBatchIdAndOutcomeIsNull(UUID batchId, Pageable pageable);

	long countByBatchIdAndOutcome(UUID batchId, UptimeRowOutcome outcome);

	Page<UptimeImportRow> findByOutcome(UptimeRowOutcome outcome, Pageable pageable);
    boolean existsByBatchIdAndTemporalPrecisionNot(UUID batchId,
        com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision precision);
}
