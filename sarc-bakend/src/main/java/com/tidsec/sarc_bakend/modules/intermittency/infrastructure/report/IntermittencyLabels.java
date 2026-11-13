package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationBand;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;

/** Textos y formatos de los informes de intermitencias; los mismos rotulos del dashboard. */
final class IntermittencyLabels {

	private static final Locale ES_EC = Locale.forLanguageTag("es-EC");

	static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES_EC);

	static final String NO_DATA = "No hay intermitencias confirmadas en el período.";

	private IntermittencyLabels() {
	}

	/** Horas acumuladas y minutos, como «36:42»: nunca se reinician a las 24 horas. */
	static String hours(long minutes) {
		return "%d:%02d".formatted(minutes / 60, minutes % 60);
	}

	static String hoursUnit(long minutes) {
		return hours(minutes) + " h";
	}

	static String count(long value) {
		return NumberFormat.getIntegerInstance(ES_EC).format(value);
	}

	/** Participacion de {@code part} en {@code total}, con un decimal; sin total no hay porcentaje. */
	static String percent(long part, long total) {
		if (total <= 0) {
			return "Sin datos";
		}
		return decimal(BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1,
				RoundingMode.HALF_UP), 1) + " %";
	}

	static String decimal(BigDecimal value, int digits) {
		NumberFormat format = NumberFormat.getNumberInstance(ES_EC);
		format.setMinimumFractionDigits(digits);
		format.setMaximumFractionDigits(digits);
		return format.format(value);
	}

	static String plural(long count, String one, String many) {
		return count(count) + " " + (count == 1 ? one : many);
	}

	static String preset(Preset preset) {
		return switch (preset) {
			case FIRST_HALF -> "Primera quincena";
			case SECOND_HALF -> "Segunda quincena";
			case LAST_15_DAYS -> "Últimos 15 días";
			case CUSTOM -> "Rango personalizado";
		};
	}

	static String band(DurationBand band) {
		return switch (band) {
			case UNDER_5 -> "Menos de 5 min";
			case FROM_5_TO_14 -> "5 a 14 min";
			case FROM_15_TO_59 -> "15 a 59 min";
			case FROM_60_TO_239 -> "1 h a menos de 4 h";
			case FROM_240 -> "4 h o más";
		};
	}

	static String state(WindowState state) {
		return switch (state) {
			case CONFIRMED -> "Confirmada";
			case OPEN -> "Abierta";
			case REVIEW -> "Por revisar";
			case TEST -> "Prueba";
			case ANNULLED -> "Anulada";
		};
	}

	/** Disponibilidad con dos decimales; sin tiempo evaluado es «Sin datos», nunca 100 %. */
	static String availability(BigDecimal percent) {
		return percent == null ? "Sin datos" : decimal(percent, 2) + " %";
	}

	static String coverage(int coveredDays, int daysInScope) {
		return count(coveredDays) + " de " + plural(daysInScope, "día", "días");
	}

	static String exclusion(AvailabilityEstimate.ExclusionReason reason) {
		return switch (reason) {
			case DISCONNECTED -> "Desconectado: instalado sin servicio";
			case INACTIVE -> "Inactivo y sin ventanas en el período";
			case DELETED -> "Eliminado y sin ventanas en el período";
		};
	}

	/** Un punto sin codigo se nombra solo por su nombre; el identificador interno nunca se muestra. */
	static String code(String code) {
		return code == null ? "" : code;
	}
}
