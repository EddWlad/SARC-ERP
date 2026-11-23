package com.tidsec.sarc_bakend.shared.error;

import java.util.Map;

/** El contenido recibido supera el tamano permitido por una regla de negocio (p. ej. el limite de una evidencia). */
public final class PayloadTooLargeException extends SarcException {

	public PayloadTooLargeException(String code, String message) {
		super(code, message, Map.of());
	}
}
