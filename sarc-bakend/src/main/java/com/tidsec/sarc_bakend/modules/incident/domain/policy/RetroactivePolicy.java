package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Duration;
import java.time.Instant;

/**
 * Cuando un hito, un evento o una comunicacion es retroactivo (FR-018, FR-041): si su hora es mas de un minuto anterior
 * al momento del registro. Una diferencia de un minuto o menos se toma como «ahora», y una hora futura se rechaza. La
 * regla es una sola para T0, los demas hitos, los eventos y las comunicaciones.
 */
public final class RetroactivePolicy {

	/** Margen dentro del cual una hora anterior sigue siendo «ahora». */
	public static final Duration TOLERANCE = Duration.ofMinutes(1);

	public enum Kind {
		/** Dentro del minuto de tolerancia: no es retroactiva ni exige motivo. */
		NOW,
		/** Mas de un minuto antes del registro: exige un motivo. */
		RETROACTIVE,
		/** Posterior al momento del registro: se rechaza. */
		FUTURE
	}

	private RetroactivePolicy() {
	}

	public static Kind evaluate(Instant occurredAt, Instant now) {
		if (occurredAt.isAfter(now)) {
			return Kind.FUTURE;
		}
		return Duration.between(occurredAt, now).compareTo(TOLERANCE) > 0 ? Kind.RETROACTIVE : Kind.NOW;
	}
}
