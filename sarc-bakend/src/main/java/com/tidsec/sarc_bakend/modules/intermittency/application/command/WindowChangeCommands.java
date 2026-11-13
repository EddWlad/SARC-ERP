package com.tidsec.sarc_bakend.modules.intermittency.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entradas de los cambios de una ventana ya guardada. Todos llevan justificacion y la version leida (FR-017, FR-018). */
public final class WindowChangeCommands {

	private WindowChangeCommands() {
	}

	/** Corrige el inicio, el fin o el comentario; lo que viene nulo no cambia. */
	public record Correct(UUID windowId, long version, Instant startAt, Instant endAt, String comment,
			String justification) {
	}

	/** Confirmar una ventana por revisar, marcarla o quitarle la marca de prueba, o anularla. */
	public record Justified(UUID windowId, long version, String justification) {
	}

	/**
	 * Marca como prueba las ventanas CONFIRMED o REVIEW de un punto en {@code [from, to)} (dias locales, fin
	 * exclusivo). Es una operacion sobre el rango, no una marca permanente del punto (FR-016).
	 */
	public record PointTest(UUID equipmentId, LocalDate from, LocalDate to, String justification) {
	}
}
