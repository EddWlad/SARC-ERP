package com.tidsec.sarc_bakend.modules.incident.domain.model.numbering;

import java.util.Optional;

/**
 * Estado de la numeracion de tickets (FR-013): el numero inicial, el siguiente, y el ambiente al que pertenece
 * (PRODUCTION o TEST). Mientras no este configurada no se crean tickets. Una vez creado el primer ticket el numero
 * inicial no cambia. El numero lo asigna el contador, nunca esta clase: aqui solo vive la forma de la configuracion.
 */
public record TicketNumbering(Integer startNumber, Integer nextNumber, String environment) {

	public static final String PRODUCTION = "PRODUCTION";
	public static final String TEST = "TEST";

	public boolean configured() {
		return startNumber != null && nextNumber != null && environment != null;
	}

	/** El codigo que tendria el siguiente ticket; informativo, porque el numero se asigna solo al crear. */
	public Optional<TicketCode> nextCode() {
		return configured() ? Optional.of(new TicketCode(nextNumber)) : Optional.empty();
	}

	public static boolean isValidEnvironment(String environment) {
		return PRODUCTION.equals(environment) || TEST.equals(environment);
	}
}
