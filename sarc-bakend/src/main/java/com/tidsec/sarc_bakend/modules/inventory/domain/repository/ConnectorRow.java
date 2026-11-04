package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Proyeccion de un conector con su tipo y su equipo, tambien para posiciones inactivas. */
public record ConnectorRow(UUID connectorId, int ordinal, String typeCode, String typeName, boolean active,
		UUID equipmentId, String equipmentName, String assignedCode, ChargingNetwork network,
		InfrastructureType type, EquipmentBrand brand, AdministrativeStatus adminStatus) {
}
