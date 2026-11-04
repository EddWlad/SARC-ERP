package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.command.UptimeRowDecisionCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UptimeRowDecisionRequest(@NotEmpty @Size(max = 1000) List<@NotNull UUID> rowIds, boolean approved,
		@Schema(nullable = true) @Size(max = 500) String justification) {

	public UptimeRowDecisionCommand toCommand(UUID batchId) {
		return new UptimeRowDecisionCommand(batchId, rowIds, approved, justification);
	}
}
