package com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Intervalo de atencion de un dia. Si cierra antes de abrir es nocturno: termina al dia
 * siguiente (por ejemplo 22:00 a 06:00).
 */
@Embeddable
public record WeeklyInterval(
		@Column(name = "day_of_week", nullable = false) DayOfWeek dayOfWeek,
		@Column(name = "opens_at", nullable = false) LocalTime opensAt,
		@Column(name = "closes_at", nullable = false) LocalTime closesAt) {

	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	public WeeklyInterval {
		if (dayOfWeek == null || opensAt == null || closesAt == null || opensAt.equals(closesAt)) {
			throw new BusinessRuleException("INVALID_SCHEDULE_INTERVAL",
					"Cada intervalo necesita dia, hora de apertura y de cierre distintas");
		}
	}

	public boolean isOvernight() {
		return closesAt.isBefore(opensAt);
	}

	/** Minuto de la semana (lunes 00:00 = 0) en que abre. */
	int startMinute() {
		return (dayOfWeek.getValue() - 1) * MINUTES_PER_DAY + opensAt.getHour() * 60 + opensAt.getMinute();
	}

	int durationMinutes() {
		int open = opensAt.getHour() * 60 + opensAt.getMinute();
		int close = closesAt.getHour() * 60 + closesAt.getMinute();
		return isOvernight() ? MINUTES_PER_DAY - open + close : close - open;
	}

	/** Se solapan si comparten algun minuto de la semana (contando el paso de domingo a lunes). */
	boolean overlaps(WeeklyInterval other) {
		return intersects(startMinute(), durationMinutes(), other.startMinute(), other.durationMinutes());
	}

	private static boolean intersects(int startA, int lengthA, int startB, int lengthB) {
		for (int shift = -MINUTES_PER_WEEK; shift <= MINUTES_PER_WEEK; shift += MINUTES_PER_WEEK) {
			int shiftedB = startB + shift;
			if (startA < shiftedB + lengthB && shiftedB < startA + lengthA) {
				return true;
			}
		}
		return false;
	}
}
