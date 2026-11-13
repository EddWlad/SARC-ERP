package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Periodos del dashboard (FR-031): primera quincena, segunda quincena, ultimos 15 dias moviles y rango
 * personalizado. Todos son rangos de dias {@code [desde, hasta)} con el fin exclusivo, como el resto de la API.
 */
public final class PeriodPresets {

	/** Un rango personalizado admite hasta este numero de dias. */
	public static final int MAX_CUSTOM_DAYS = 366;

	private static final int FIRST_HALF_LAST_DAY = 15;

	public enum Preset {
		FIRST_HALF, SECOND_HALF, LAST_15_DAYS, CUSTOM
	}

	/** Periodo consultado: el tipo elegido y su rango real, con el fin exclusivo. */
	public record Period(Preset preset, LocalDate from, LocalDate to) {

		public long days() {
			return ChronoUnit.DAYS.between(from, to);
		}
	}

	private PeriodPresets() {
	}

	/**
	 * Normaliza lo que pide quien consulta. Sin nada, la quincena en curso; las quincenas usan {@code month} (el
	 * mes de hoy si falta); un rango personalizado exige {@code from} y {@code to}, y se infiere si solo vienen
	 * estos.
	 */
	public static Period resolve(Preset preset, YearMonth month, LocalDate from, LocalDate to, LocalDate today) {
		Preset chosen = preset;
		if (chosen == null) {
			chosen = from != null || to != null ? Preset.CUSTOM : currentFortnight(today);
		}
		YearMonth base = month == null ? YearMonth.from(today) : month;
		return switch (chosen) {
			case FIRST_HALF -> new Period(chosen, base.atDay(1), base.atDay(FIRST_HALF_LAST_DAY + 1));
			case SECOND_HALF -> new Period(chosen, base.atDay(FIRST_HALF_LAST_DAY + 1), base.plusMonths(1).atDay(1));
			case LAST_15_DAYS -> new Period(chosen, today.minusDays(14), today.plusDays(1));
			case CUSTOM -> custom(from, to);
		};
	}

	/** La quincena en curso: del dia 1 al 15 es la primera; del 16 en adelante, la segunda (FR-031). */
	public static Preset currentFortnight(LocalDate today) {
		return today.getDayOfMonth() <= FIRST_HALF_LAST_DAY ? Preset.FIRST_HALF : Preset.SECOND_HALF;
	}

	private static Period custom(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			throw new BusinessRuleException("PERIOD_INVALID", "El rango personalizado necesita el primer y el ultimo dia");
		}
		if (!to.isAfter(from)) {
			throw new BusinessRuleException("PERIOD_INVALID", "El rango debe tener al menos un dia: el fin es exclusivo");
		}
		Period period = new Period(Preset.CUSTOM, from, to);
		if (period.days() > MAX_CUSTOM_DAYS) {
			throw new BusinessRuleException("PERIOD_TOO_LONG",
					"El rango personalizado admite hasta " + MAX_CUSTOM_DAYS + " dias");
		}
		return period;
	}
}
