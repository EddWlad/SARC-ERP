package com.tidsec.sarc_bakend.shared.error;

/** El recurso solicitado no existe. */
public final class NotFoundException extends SarcException {

	public NotFoundException(String code, String message) {
		super(code, message);
	}
}
