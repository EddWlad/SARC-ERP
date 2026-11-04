package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;

interface MeasurementRevisionJpaRepository extends JpaRepository<MeasurementRevision, UUID> {

	Optional<MeasurementRevision> findByMeasurementIdAndRevision(UUID measurementId, int revision);

	List<MeasurementRevision> findByMeasurementIdOrderByRevisionDesc(UUID measurementId);

	List<MeasurementRevision> findByMeasurementIdIn(Collection<UUID> measurementIds);
}
