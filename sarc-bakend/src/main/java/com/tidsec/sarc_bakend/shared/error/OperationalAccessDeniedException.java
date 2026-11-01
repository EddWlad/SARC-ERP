package com.tidsec.sarc_bakend.shared.error;

import org.springframework.security.access.AccessDeniedException;

/** Denegación 403 contextual; nunca contiene cantidades ni referencias de almacenamiento. */
public final class OperationalAccessDeniedException extends AccessDeniedException {

	private final String code;

	public OperationalAccessDeniedException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
