package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

/** Duracion legible en horas acumuladas y minutos, por ejemplo 36:42 (FR-009). */
public final class DurationText {

	private DurationText() {
	}

	public static String hoursAndMinutes(long minutes) {
		if (minutes < 0) {
			throw new IllegalArgumentException("Una duracion no es negativa");
		}
		return "%d:%02d".formatted(minutes / 60, minutes % 60);
	}
}
