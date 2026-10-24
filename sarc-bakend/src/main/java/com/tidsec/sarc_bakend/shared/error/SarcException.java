package com.tidsec.sarc_bakend.shared.error;

import java.util.Map;

/**
 * Base de excepciones de negocio. Lleva un codigo estable para el cliente y, si hace falta,
 * datos adicionales que el cliente necesita para reaccionar (por ejemplo el id de un registro
 * eliminado que puede recuperarse). No conoce HTTP.
 */
public abstract sealed class SarcException extends RuntimeException
		permits NotFoundException, ConflictException, BusinessRuleException, ServiceUnavailableException,
		TooManyRequestsException {

	private final String code;
	private final Map<String, Object> properties;

	protected SarcException(String code, String message, Map<String, Object> properties) {
		super(message);
		this.code = code;
		this.properties = Map.copyOf(properties);
	}

	public String code() {
		return code;
	}

	public Map<String, Object> properties() {
		return properties;
	}
}
