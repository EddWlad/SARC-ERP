package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Detalle de un equipo. {@code uidPuk} y {@code ocppUrl} solo llegan a administradores y analistas;
 * {@code restrictedDataVisible} indica si el usuario puede verlos.
 */
public record EquipmentResponse(
		UUID id,
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentBrand brand,
		String name,
		String serial,
		String assignedCode,
		@Schema(nullable = true) String uidPuk,
		@Schema(nullable = true) String location,
		@Schema(nullable = true) String mapsUrl,
		@Schema(nullable = true) String ocppUrl,
		@Schema(nullable = true) String imageUrl,
		AdministrativeStatus adminStatus,
		@Schema(nullable = true) UsageStatus usageStatus,
		@Schema(nullable = true) UUID replacesEquipmentId,
		List<ConnectorResponse> connectors,
		ScheduleResponse schedule,
		boolean restrictedDataVisible,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	public static EquipmentResponse from(EquipmentView view) {
		return new EquipmentResponse(view.id(), view.network(), view.type(), view.brand(), view.name(), view.serial(),
				view.assignedCode(), view.uidPuk(), view.location(), view.mapsUrl(), view.ocppUrl(), view.imageUrl(),
				view.adminStatus(), view.usageStatus(), view.replacesEquipmentId(),
				view.connectors().stream().map(ConnectorResponse::from).toList(), ScheduleResponse.from(view),
				view.restrictedDataVisible(), view.createdAt(), view.updatedAt(), view.version());
	}
}
