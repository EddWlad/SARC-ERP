package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.application.command.EquipmentRowDecisionCommand;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Aprueba o no la actualizacion de los equipos existentes de esas filas. */
public record EquipmentRowDecisionRequest(
		@NotEmpty @Size(max = 1000) List<@NotNull UUID> rowIds,
		@NotNull Boolean approved) {

	public EquipmentRowDecisionCommand toCommand(UUID batchId) {
		return new EquipmentRowDecisionCommand(batchId, rowIds, approved);
	}
}
