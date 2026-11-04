package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Uptime = (evaluado - fuera de linea) x 100 / evaluado. Ocupado y Con falla no descuentan. */
public final class UptimeFormula {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private UptimeFormula() {
	}

	/** Dos decimales {@code HALF_UP}; sin tiempo evaluado no hay valor, nunca 0. */
	public static BigDecimal percent(long evaluatedMinutes, long offlineMinutes) {
		return percent(evaluatedMinutes, BigDecimal.valueOf(offlineMinutes));
	}

	/** Igual, con lo que descuenta en fraccion de minuto (uptime informado por la fuente). */
	public static BigDecimal percent(long evaluatedMinutes, BigDecimal downMinutes) {
		if (evaluatedMinutes == 0) {
			return null;
		}
		return BigDecimal.valueOf(evaluatedMinutes).subtract(downMinutes).multiply(HUNDRED)
				.divide(BigDecimal.valueOf(evaluatedMinutes), 2, RoundingMode.HALF_UP);
	}
}
