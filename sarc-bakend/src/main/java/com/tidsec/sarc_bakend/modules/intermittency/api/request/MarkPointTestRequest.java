package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.WindowChangeCommands;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Rango de dias (fin exclusivo) y justificacion para marcar o quitar la prueba de un punto. */
public record MarkPointTestRequest(@NotNull LocalDate from, @NotNull LocalDate to,
		@Size(max = 1000) String justification) {

	public WindowChangeCommands.PointTest toCommand(UUID equipmentId) {
		return new WindowChangeCommands.PointTest(equipmentId, from, to, justification);
	}
}
