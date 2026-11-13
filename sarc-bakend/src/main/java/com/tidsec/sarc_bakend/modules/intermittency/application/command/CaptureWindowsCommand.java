package com.tidsec.sarc_bakend.modules.intermittency.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Captura manual: las ventanas de un punto. No hay un limite funcional de ventanas por dia (FR-010). */
public record CaptureWindowsCommand(UUID equipmentId, List<WindowInput> windows) {

	public CaptureWindowsCommand {
		windows = List.copyOf(windows);
	}

	/** Una ventana: inicio, fin opcional (sin fin queda abierta) y comentario de hasta 500 caracteres. */
	public record WindowInput(Instant startAt, Instant endAt, String comment) {
	}
}
