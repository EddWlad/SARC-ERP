package com.tidsec.sarc_bakend.modules.identity.domain.policy;

import java.time.Duration;
import java.time.Instant;

/** Politica anti fuerza bruta: intentos fallidos tolerados y duracion del bloqueo temporal. */
public record LoginPolicy(int maxFailedAttempts, Duration lockoutDuration) {

	public LoginPolicy {
		if (maxFailedAttempts < 1) {
			throw new IllegalArgumentException("maxFailedAttempts debe ser >= 1");
		}
		if (lockoutDuration.isZero() || lockoutDuration.isNegative()) {
			throw new IllegalArgumentException("lockoutDuration debe ser positiva");
		}
	}

	/** Fin del bloqueo si la cuenta se bloquea en el instante {@code now}. */
	public Instant lockUntil(Instant now) {
		return now.plus(lockoutDuration);
	}
}
