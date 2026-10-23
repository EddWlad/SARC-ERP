package com.tidsec.sarc_bakend.shared.error;

/** La operacion choca con el estado actual del recurso (duplicado, version obsoleta). */
public final class ConflictException extends SarcException {

	public ConflictException(String code, String message) {
		super(code, message);
	}
}
