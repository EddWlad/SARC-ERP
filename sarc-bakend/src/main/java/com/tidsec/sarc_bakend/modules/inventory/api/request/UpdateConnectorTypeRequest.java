package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.inventory.application.command.UpdateConnectorTypeCommand;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Renombrar o activar/inactivar un tipo de conector; los campos omitidos no cambian. */
public record UpdateConnectorTypeRequest(@NotNull Long version, @Size(min = 1, max = 100) String name,
		AdministrativeStatus status) {

	public UpdateConnectorTypeCommand toCommand(UUID id) {
		return new UpdateConnectorTypeCommand(id, version, name, status);
	}
}
