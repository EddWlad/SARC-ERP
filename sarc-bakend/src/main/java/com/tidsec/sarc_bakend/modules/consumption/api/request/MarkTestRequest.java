package com.tidsec.sarc_bakend.modules.consumption.api.request;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.application.command.MarkSessionTestCommand;

import jakarta.validation.constraints.NotNull;

public record MarkTestRequest(@NotNull Long version, @NotNull Boolean test) {

	public MarkSessionTestCommand toCommand(UUID id) {
		return new MarkSessionTestCommand(id, version, test);
	}
}
