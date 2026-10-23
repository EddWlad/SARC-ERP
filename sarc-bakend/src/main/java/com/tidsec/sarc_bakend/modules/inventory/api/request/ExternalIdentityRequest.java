package com.tidsec.sarc_bakend.modules.inventory.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.tidsec.sarc_bakend.modules.inventory.application.command.RegisterExternalIdentityCommand;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

/** Identificador del equipo en una plataforma; {@code scope} distingue cuentas o sitios (opcional). */
public record ExternalIdentityRequest(
		@NotNull ExternalPlatform platform,
		@Size(max = 100) String scope,
		@NotBlank @Size(max = 150) String externalId,
		@Size(max = 150) String alias) {

	public RegisterExternalIdentityCommand toCommand(UUID equipmentId) {
		return new RegisterExternalIdentityCommand(equipmentId, platform, scope, externalId, alias);
	}
}
