package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.inventory.application.command.UpdateEquipmentCommand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentDetails;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;

/** Reemplaza los datos editables del equipo (red y tipo no cambian). {@code version} la leyo el cliente. */
public record UpdateEquipmentRequest(
		@NotNull Long version,
		@NotNull EquipmentBrand brand,
		@NotBlank @Size(max = 150) String name,
		@NotBlank @Size(max = 80) String serial,
		@Size(max = 80) String assignedCode,
		@Size(max = 80) String uidPuk,
		@Size(max = 300) String location,
		@Size(max = 500) String mapsUrl,
		@Size(max = 500) String ocppUrl,
		UsageStatus usageStatus,
		@NotEmpty @Size(max = 3) List<UUID> connectorTypeIds) {

	public UpdateEquipmentCommand toCommand(UUID id) {
		return new UpdateEquipmentCommand(id, version, new EquipmentDetails(brand, name, serial, assignedCode, uidPuk,
				location, mapsUrl, ocppUrl, usageStatus), connectorTypeIds);
	}
}
