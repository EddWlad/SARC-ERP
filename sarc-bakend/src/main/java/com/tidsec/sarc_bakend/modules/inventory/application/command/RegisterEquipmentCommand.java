package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentDetails;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Alta de un equipo con sus conectores en orden de posicion; {@code replacesEquipmentId} es opcional. */
public record RegisterEquipmentCommand(
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentDetails details,
		List<UUID> connectorTypeIds,
		UUID replacesEquipmentId) {
}
