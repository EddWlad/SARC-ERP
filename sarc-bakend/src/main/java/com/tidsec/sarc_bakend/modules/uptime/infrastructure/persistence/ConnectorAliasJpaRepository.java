package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.ConnectorAlias;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;

interface ConnectorAliasJpaRepository extends JpaRepository<ConnectorAlias, UUID> {

	List<ConnectorAlias> findBySourceAndEquipmentIdIn(MeasurementSource source, Collection<UUID> equipmentIds);

	Optional<ConnectorAlias> findBySourceAndEquipmentIdAndLabelKey(MeasurementSource source, UUID equipmentId,
			String labelKey);

	Page<ConnectorAlias> findByEquipmentId(UUID equipmentId, Pageable pageable);
}
