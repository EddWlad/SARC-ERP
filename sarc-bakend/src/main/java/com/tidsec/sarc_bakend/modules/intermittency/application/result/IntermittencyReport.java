package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationBand;

/**
 * Lo que se dibuja en un informe de intermitencias: las mismas cifras del dashboard ({@code summary}), mas el
 * detalle por cargador y por ventana confirmada que permite comprobarlas. {@code operationalAllowed} es verdadero
 * solo para quien puede leer lo operativo: ese informe incluye el anexo de lo que no es oficial (FR-038).
 * {@code availability} es nulo para quien no tiene el permiso de disponibilidad: ese informe no la trae (FR-006).
 */
public record IntermittencyReport(String scope, Instant cutOff, IntermittencySummary summary,
		boolean operationalAllowed, AvailabilityEstimate availability, List<PointRow> points,
		List<WindowRow> windows) {

	/**
	 * Un cargador con tiempo confirmado. {@code percentOfDays} son los dias afectados sobre los dias calendario
	 * del periodo hasta el dia del corte, o nulo si todavia no hay ninguno.
	 */
	public record PointRow(String code, String name, int days, BigDecimal percentOfDays,
			int windows, long minutes) {
	}

	/** Una ventana confirmada con el tiempo que aporta al periodo y la franja de su duracion total. */
	public record WindowRow(String code, String name, String pointLabel, Instant startAt, Instant endAt,
			long durationMinutes, long portionMinutes, DurationBand band) {
	}
}
