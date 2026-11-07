package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementTemporalChange;

interface MeasurementTemporalChangeJpaRepository extends JpaRepository<MeasurementTemporalChange,UUID> {
    Optional<MeasurementTemporalChange> findByMeasurementIdAndExpectedMeasurementVersionAndPolicyVersion(
        UUID measurementId,long version,String policyVersion);
    List<MeasurementTemporalChange> findByMeasurementIdOrderByOccurredAtDesc(UUID measurementId);
}
