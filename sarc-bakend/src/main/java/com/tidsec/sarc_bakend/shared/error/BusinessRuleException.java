package com.tidsec.sarc_bakend.shared.error;

import java.util.Map;

/** Se incumple una regla de negocio del dominio. */
public final class BusinessRuleException extends SarcException {

	public BusinessRuleException(String code, String message) {
		super(code, message, Map.of());
	}
}
