package com.tidsec.sarc_bakend.shared.error;

/** Se incumple una regla de negocio del dominio. */
public class BusinessRuleException extends SarcException {

	public BusinessRuleException(String code, String message) {
		super(code, message);
	}
}
