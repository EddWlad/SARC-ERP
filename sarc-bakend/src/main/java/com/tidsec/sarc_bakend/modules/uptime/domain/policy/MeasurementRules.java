package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Clasificacion unica de una lectura, para captura, pegado, importacion y correccion. La suma de
 * estados se compara con la duracion real, con tolerancia de un minuto (Decision 4); en periodos de mas
 * de siete dias, el 0,02 % de la duracion redondeado hacia arriba, si es mayor (DP-05, decidido con la
 * muestra real).
 */
public final class MeasurementRules {

	private static final long TOLERANCE_MINUTES = 1;
	private static final long LONG_PERIOD_MINUTES = 7 * 24 * 60;
	private static final long LONG_PERIOD_TOLERANCE_PER_TEN_THOUSAND = 2;
	private static final BigDecimal PERCENT_TOLERANCE = new BigDecimal("0.01");
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal MAX_PERCENT = new BigDecimal("99999999.9999");
	private static final BigDecimal MAX_MINUTES = BigDecimal.valueOf(Integer.MAX_VALUE);

	private MeasurementRules() {
	}

	/**
	 * Valores tal como llegaron, antes de saber si caben en minutos enteros. Con
	 * {@code REPORTED_PERCENT} no hay Ocupado y el uptime es el porcentaje de la fuente.
	 */
	public record Values(BigDecimal available, BigDecimal occupied, BigDecimal fault, BigDecimal offline,
			BigDecimal reportedPercent, UptimeBasis basis) {

		public Values(BigDecimal available, BigDecimal occupied, BigDecimal fault, BigDecimal offline,
				BigDecimal reportedPercent) {
			this(available, occupied, fault, offline, reportedPercent, UptimeBasis.STATES);
		}
	}

	/** Minutos que cupieron, observaciones y porcentaje calculado cuando la lectura lo admite. */
	public record Assessment(Integer available, Integer occupied, Integer fault, Integer offline,
			List<Observation> observations, BigDecimal percent, UptimeBasis basis) {

		public boolean validatable() {
			return observations.stream().allMatch(Observation::validatable);
		}

		/** Un valor no cupo en minutos: el original solo sobrevive en {@code source_values}. */
		public boolean needsSourceValues() {
			return available == null || fault == null || offline == null
					|| (basis == UptimeBasis.STATES && occupied == null);
		}
	}

	public static Assessment assess(Values values, long durationMinutes, PeriodType type) {
		BigDecimal reported = values.reportedPercent();
		if (reported != null && (reported.abs().compareTo(MAX_PERCENT) > 0 || reported.scale() > 4)) {
			throw new BusinessRuleException("UPTIME_PERCENT_TOO_LARGE", "El porcentaje informado no se puede registrar.");
		}
		if (values.basis() == UptimeBasis.REPORTED_PERCENT) {
			return assessReported(values, durationMinutes, type);
		}
		Integer available = minutes(values.available());
		Integer occupied = minutes(values.occupied());
		Integer fault = minutes(values.fault());
		Integer offline = minutes(values.offline());
		boolean allMinutes = available != null && occupied != null && fault != null && offline != null;

		List<Observation> observations = new ArrayList<>();
		if (!type.isExact() && Math.abs(durationMinutes - type.nominal().toMinutes()) > TOLERANCE_MINUTES) {
			observations.add(Observation.WINDOW_DURATION_MISMATCH);
		}
		BigDecimal computed = null;
		if (!allMinutes) {
			observations.add(Observation.DURATION_INVALID);
		}
		else {
			long sum = (long) available + occupied + fault + offline;
			long tolerance = sumTolerance(durationMinutes);
			if (sum == 0) {
				observations.add(Observation.SUM_ZERO);
			}
			else if (sum > durationMinutes + tolerance) {
				observations.add(Observation.SUM_ABOVE_DURATION);
			}
			else if (sum < durationMinutes - tolerance) {
				observations.add(Observation.SUM_BELOW_DURATION);
			}
			computed = UptimeFormula.percent(sum, offline);
		}
		if (reported != null) {
			if (reported.signum() < 0 || reported.compareTo(HUNDRED) > 0) {
				observations.add(Observation.PERCENT_OUT_OF_RANGE);
			}
			else if (computed != null && reported.subtract(computed).abs().compareTo(PERCENT_TOLERANCE) > 0) {
				observations.add(Observation.PERCENT_MISMATCH);
			}
		}
		boolean presentable = observations.stream().allMatch(Observation::validatable);
		return new Assessment(available, occupied, fault, offline, List.copyOf(observations),
				presentable ? computed : null, UptimeBasis.STATES);
	}

	/**
	 * Informe semanal sin Ocupado: el porcentaje de la fuente es el valor. Los tres estados que llegan no
	 * pueden pasar de la duracion; no se compara el porcentaje porque sin Ocupado no hay tiempo evaluado.
	 */
	private static Assessment assessReported(Values values, long durationMinutes, PeriodType type) {
		BigDecimal reported = values.reportedPercent();
		if (reported == null) {
			throw new BusinessRuleException("UPTIME_PERCENT_REQUIRED",
					"Sin la columna Cargando, el uptime informado es obligatorio.");
		}
		Integer available = minutes(values.available());
		Integer fault = minutes(values.fault());
		Integer offline = minutes(values.offline());
		List<Observation> observations = new ArrayList<>();
		if (!type.isExact() && Math.abs(durationMinutes - type.nominal().toMinutes()) > TOLERANCE_MINUTES) {
			observations.add(Observation.WINDOW_DURATION_MISMATCH);
		}
		if (available == null || fault == null || offline == null) {
			observations.add(Observation.DURATION_INVALID);
		}
		else {
			long sum = (long) available + fault + offline;
			if (sum == 0) {
				observations.add(Observation.SUM_ZERO);
			}
			else if (sum > durationMinutes + sumTolerance(durationMinutes)) {
				observations.add(Observation.SUM_ABOVE_DURATION);
			}
		}
		if (reported.signum() < 0 || reported.compareTo(HUNDRED) > 0) {
			observations.add(Observation.PERCENT_OUT_OF_RANGE);
		}
		boolean presentable = observations.stream().allMatch(Observation::validatable);
		return new Assessment(available, null, fault, offline, List.copyOf(observations),
				presentable ? reported.setScale(2, RoundingMode.HALF_UP) : null, UptimeBasis.REPORTED_PERCENT);
	}

	/** En periodos largos el redondeo de las horas de Enerlink se acumula: se admite el 0,02 %. */
	static long sumTolerance(long durationMinutes) {
		if (durationMinutes <= LONG_PERIOD_MINUTES) {
			return TOLERANCE_MINUTES;
		}
		return Math.max(TOLERANCE_MINUTES,
				Math.ceilDiv(durationMinutes * LONG_PERIOD_TOLERANCE_PER_TEN_THOUSAND, 10_000));
	}

	private static Integer minutes(BigDecimal value) {
		if (value == null || value.signum() < 0 || value.compareTo(MAX_MINUTES) > 0
				|| value.stripTrailingZeros().scale() > 0) {
			return null;
		}
		return value.intValueExact();
	}
}
