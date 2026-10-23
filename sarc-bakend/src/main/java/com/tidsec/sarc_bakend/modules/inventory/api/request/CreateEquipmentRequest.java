package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.inventory.application.command.RegisterEquipmentCommand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentDetails;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;

/**
 * Alta de un equipo. {@code assignedCode} solo se envia en puntos KIA; en los demas es la serie.
 * {@code connectorTypeIds} va en orden de posicion (1 en puntos, 1 a 3 en electrolineras).
 */
public record CreateEquipmentRequest(
		@NotNull ChargingNetwork network,
		@NotNull InfrastructureType type,
		@NotNull EquipmentBrand brand,
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 80) String serial,
		@Size(max = 80) String assignedCode,
		@Size(max = 80) String uidPuk,
		@Size(max = 300) String location,
		@Size(max = 500) String mapsUrl,
		@Size(max = 500) String ocppUrl,
		UsageStatus usageStatus,
		@NotEmpty @Size(max = 3) List<UUID> connectorTypeIds,
		UUID replacesEquipmentId) {

	public RegisterEquipmentCommand toCommand() {
		return new RegisterEquipmentCommand(network, type, new EquipmentDetails(brand, name, serial, assignedCode, uidPuk,
				location, mapsUrl, ocppUrl, usageStatus), connectorTypeIds, replacesEquipmentId);
	}
}
