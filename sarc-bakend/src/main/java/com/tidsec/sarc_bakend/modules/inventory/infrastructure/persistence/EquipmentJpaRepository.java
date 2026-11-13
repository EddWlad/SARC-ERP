package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

interface EquipmentJpaRepository extends JpaRepository<Equipment, UUID>, JpaSpecificationExecutor<Equipment> {

	List<Equipment> findBySerialIn(Collection<String> serials);

	List<Equipment> findByAssignedCodeIn(Collection<String> assignedCodes);

	List<Equipment> findByNetworkAndType(ChargingNetwork network, InfrastructureType type);
}
