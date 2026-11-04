package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lectura para indicadores: valores de la revision vigente o, sin vigente, de la ultima. Las marcas
 * {@code latestPending} y {@code latestObserved} describen la ultima revision y son operativas.
 * {@code downMinutes} es lo que descuenta del uptime: Fuera de linea o, con uptime informado, la parte
 * del periodo que el porcentaje de la fuente deja fuera.
 */
public record MeasurementFact(UUID measurementId, UUID connectorId, UUID equipmentId, Instant periodStart,
		Instant periodEnd, PeriodType periodType, ReadingClass readingClass, boolean validated, boolean excluded,
		long evaluatedMinutes, long offlineMinutes, long faultMinutes, boolean latestPending, boolean latestObserved,
		BigDecimal downMinutes) {

	/** data-model §8: vigente, no excluida, medida y con tiempo evaluado. */
	public boolean included() {
		return validated && !excluded && readingClass == ReadingClass.MEASURED && evaluatedMinutes > 0;
	}
}
