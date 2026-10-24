package com.tidsec.sarc_bakend.modules.identity.domain.policy;

import java.time.Duration;

/**
 * Limite de intentos fallidos por origen (TRD 16.1): complementa el bloqueo por cuenta, que no frena
 * a quien prueba una misma clave contra muchos correos. Superado el limite, el origen espera hasta
 * que termine su ventana.
 */
public record LoginThrottlePolicy(int maxFailures, Duration window) {

	public LoginThrottlePolicy {
		if (maxFailures < 1) {
			throw new IllegalArgumentException("maxFailures debe ser >= 1");
		}
		if (window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("window debe ser positiva");
		}
	}
}
