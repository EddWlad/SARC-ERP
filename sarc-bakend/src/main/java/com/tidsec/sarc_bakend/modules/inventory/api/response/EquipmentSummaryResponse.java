package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.modules.inventory.application.result.ConnectorView;
import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Fila del listado de inventario (sin horario ni datos restringidos). */
public record EquipmentSummaryResponse(
		UUID id,
		String name,
		@JsonInclude(JsonInclude.Include.ALWAYS) @Schema(nullable = true) String assignedCode,
		String serial,
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentBrand brand,
		AdministrativeStatus adminStatus,
		@Schema(nullable = true) UsageStatus usageStatus,
		@Schema(nullable = true) String location,
		List<String> connectorTypeCodes,
		@Schema(nullable = true) String imageUrl,
		long version) {

	public static EquipmentSummaryResponse from(EquipmentView view) {
		return new EquipmentSummaryResponse(view.id(), view.name(), view.assignedCode(), view.serial(), view.network(),
				view.type(), view.brand(), view.adminStatus(), view.usageStatus(), view.location(),
				view.connectors().stream().map(ConnectorView::connectorTypeCode).toList(), view.imageUrl(),
				view.version());
	}
}
