package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.CaptureWindowsCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Las ventanas de un punto. El tope de 500 por peticion es solo una proteccion tecnica: el sistema no limita a
 * cinco las ventanas de un punto y dia (FR-010).
 */
public record CaptureWindowsRequest(@NotNull UUID equipmentId,
		@NotEmpty @Size(max = 500) List<@Valid @NotNull WindowEntry> windows) {

	/** Inicio y fin con fecha, hora y desfase completos; sin fin, la ventana queda abierta. */
	public record WindowEntry(@NotNull OffsetDateTime startAt, OffsetDateTime endAt,
			@Size(max = 500) String comment) {
	}

	public CaptureWindowsCommand toCommand() {
		return new CaptureWindowsCommand(equipmentId,
				windows.stream().map(entry -> new CaptureWindowsCommand.WindowInput(entry.startAt().toInstant(),
						entry.endAt() == null ? null : entry.endAt().toInstant(), entry.comment())).toList());
	}
}
