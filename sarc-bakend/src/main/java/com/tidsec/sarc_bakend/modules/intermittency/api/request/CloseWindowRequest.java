package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.CloseWindowCommand;

import jakarta.validation.constraints.NotNull;

public record CloseWindowRequest(@NotNull Long version, @NotNull OffsetDateTime endAt) {

	public CloseWindowCommand toCommand(UUID windowId) {
		return new CloseWindowCommand(windowId, version, endAt.toInstant());
	}
}
