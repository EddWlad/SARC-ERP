package com.tidsec.sarc_bakend.shared.error;

/** Base de excepciones de negocio. Lleva un codigo estable para el cliente; no conoce HTTP. */
public abstract sealed class SarcException extends RuntimeException
		permits NotFoundException, ConflictException, BusinessRuleException {

	private final String code;

	protected SarcException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
