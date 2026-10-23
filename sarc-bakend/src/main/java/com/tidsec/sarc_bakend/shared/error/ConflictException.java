package com.tidsec.sarc_bakend.shared.error;

import java.util.Map;

/** La operacion choca con el estado actual del recurso (duplicado, version obsoleta). */
public final class ConflictException extends SarcException {

	public ConflictException(String code, String message) {
		this(code, message, Map.of());
	}

	public ConflictException(String code, String message, Map<String, Object> properties) {
		super(code, message, properties);
	}
}
