package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.WindowChangeCommands;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Version leida y justificacion de un cambio de estado (confirmar, prueba, anular). */
public record WindowChangeRequest(@NotNull Long version, @Size(max = 1000) String justification) {

	public WindowChangeCommands.Justified toCommand(UUID windowId) {
		return new WindowChangeCommands.Justified(windowId, version, justification);
	}
}
