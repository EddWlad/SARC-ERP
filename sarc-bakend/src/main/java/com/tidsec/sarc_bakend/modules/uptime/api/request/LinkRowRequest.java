package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LinkRowRequest(@NotNull Long version, @NotNull UUID connectorId,
		@Schema(nullable = true) Boolean rememberLabel) {

	public boolean remembers() {
		return Boolean.TRUE.equals(rememberLabel);
	}
}
