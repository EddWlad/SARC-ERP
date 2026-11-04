package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Reglas del periodo real consultado, en la zona operativa. El fin es exclusivo. */
public final class PeriodRules {

	private PeriodRules() {
	}

	public static void check(Instant start, Instant end, PeriodType type, boolean exactConfirmed, Instant now,
			ZoneId zone) {
		if (!end.isAfter(start)) {
			throw new BusinessRuleException("UPTIME_PERIOD_INVALID", "El fin del período debe ser posterior al inicio.");
		}
		if (end.isAfter(now)) {
			throw new BusinessRuleException("UPTIME_PERIOD_FUTURE", "El fin del período no puede estar en el futuro.");
		}
		if (type.isExact() && !isAlignedWeek(start, end, zone)) {
			throw new BusinessRuleException("UPTIME_EXACT_WEEK_NOT_ALIGNED",
					"Una semana exacta va de lunes 00:00 al lunes siguiente 00:00.");
		}
		if (type.isExact() && !exactConfirmed) {
			throw new BusinessRuleException("UPTIME_EXACT_WEEK_NOT_CONFIRMED",
					"Confirma de forma expresa que es una semana exacta.");
		}
	}

	public static boolean isAlignedWeek(Instant start, Instant end, ZoneId zone) {
		ZonedDateTime from = start.atZone(zone);
		return from.getDayOfWeek() == DayOfWeek.MONDAY && from.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
				&& from.plusDays(7).toInstant().equals(end);
	}

	public static long durationMinutes(Instant start, Instant end) {
		return Duration.between(start, end).toMinutes();
	}
}
