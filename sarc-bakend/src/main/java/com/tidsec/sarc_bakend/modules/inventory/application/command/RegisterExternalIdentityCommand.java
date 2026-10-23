package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ExternalPlatform;

public record RegisterExternalIdentityCommand(UUID equipmentId, ExternalPlatform platform, String scope,
		String externalId, String alias) {
}
