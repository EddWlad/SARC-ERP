package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Un instante es futuro si es posterior al minuto en curso (FR-021b). El minuto en curso se acepta completo:
 * quien registra a las 14:05:20 puede escribir 14:05.
 */
public final class FutureInstantPolicy {

	private FutureInstantPolicy() {
	}

	public static boolean isFuture(Instant instant, Instant now) {
		return instant.isAfter(now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(59));
	}
}
