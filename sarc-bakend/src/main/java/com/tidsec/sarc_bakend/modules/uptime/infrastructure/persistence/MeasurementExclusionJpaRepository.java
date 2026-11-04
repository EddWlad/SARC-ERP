package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;

interface MeasurementExclusionJpaRepository extends JpaRepository<MeasurementExclusion, UUID> {

	List<MeasurementExclusion> findByMeasurementIdOrderByDecidedAtDesc(UUID measurementId);
}
