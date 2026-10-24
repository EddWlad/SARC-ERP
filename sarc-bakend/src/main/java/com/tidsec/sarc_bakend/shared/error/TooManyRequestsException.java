package com.tidsec.sarc_bakend.shared.error;

import java.time.Duration;
import java.util.Map;

/** Demasiados intentos en poco tiempo; el cliente puede volver a intentar tras {@code retryAfter}. */
public final class TooManyRequestsException extends SarcException {

	private final Duration retryAfter;

	public TooManyRequestsException(String code, String message, Duration retryAfter) {
		super(code, message, Map.of());
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return retryAfter;
	}
}
