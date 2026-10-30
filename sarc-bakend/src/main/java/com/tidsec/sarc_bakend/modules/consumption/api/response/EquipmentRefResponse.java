package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Equipo del inventario al que pertenece una sesion o un renglon del ranking. */
public record EquipmentRefResponse(UUID id, String name, @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(nullable = true) String assignedCode, ChargingNetwork network,
		InfrastructureType type) {

	public static EquipmentRefResponse from(EquipmentReference equipment) {
		return equipment == null ? null : new EquipmentRefResponse(equipment.id(), equipment.name(),
				equipment.assignedCode(), equipment.network(), equipment.type());
	}
}
