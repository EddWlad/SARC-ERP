package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentDetails;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;

/** Alta de un equipo con sus conectores en orden de posicion; {@code replacesEquipmentId} es opcional. */
public record RegisterEquipmentCommand(
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentDetails details,
		List<UUID> connectorTypeIds,
		UUID replacesEquipmentId) {
}
