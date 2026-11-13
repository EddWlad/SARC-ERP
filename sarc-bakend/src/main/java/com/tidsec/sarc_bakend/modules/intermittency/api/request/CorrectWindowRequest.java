package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.WindowChangeCommands;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Correccion de una ventana: lo que viene nulo no cambia. La justificacion es obligatoria; se valida en el
 * dominio para responder con {@code JUSTIFICATION_REQUIRED} (FR-017).
 */
public record CorrectWindowRequest(@NotNull Long version, OffsetDateTime startAt, OffsetDateTime endAt,
		@Size(max = 500) String comment, @Size(max = 1000) String justification) {

	public WindowChangeCommands.Correct toCommand(UUID windowId) {
		return new WindowChangeCommands.Correct(windowId, version, startAt == null ? null : startAt.toInstant(),
				endAt == null ? null : endAt.toInstant(), comment, justification);
	}
}
