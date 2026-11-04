package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record UpdateConnectorAliasRequest(@NotNull Long version, @NotNull UUID connectorId, boolean active) {
}
