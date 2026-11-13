package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate.ExclusionReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Disponibilidad estimada de prueba (FR-042 a FR-046a). Solo la reciben quienes tienen el permiso de
 * disponibilidad. {@code percent} nulo es «Sin datos»; nunca se presenta como 100 %. Es una estimacion con los
 * dias revisados como cobertura, no una medicion de Enerlink ni de Uptime.
 */
public record AvailabilityResponse(AvailabilityPeriod period, AvailabilityAggregate aggregate,
		AvailabilityCoverage coverage, List<BlockedDayRow> blockedDays, List<AvailabilityPointRow> points,
		List<AvailabilityExclusion> exclusions, AvailabilityLabels labels, List<String> limitations) {

	/** Tipo de periodo y su rango real: {@code to} es exclusivo; {@code cutOff} es el momento de la consulta. */
	public record AvailabilityPeriod(Preset preset, LocalDate from, LocalDate to, Instant cutOff, String timeZone) {
	}

	/** Agregado ponderado por minutos: suma de tiempo evaluado y fuera de linea, no promedio de porcentajes. */
	public record AvailabilityAggregate(@Schema(nullable = true) BigDecimal percent, long evaluatedMinutes,
			long offlineMinutes) {
	}

	/** Dias cubiertos sobre los dias del rango ya terminados hasta el corte; hoy y los futuros no cuentan. */
	public record AvailabilityCoverage(int coveredDays, int daysInScope) {
	}

	/** Un dia que las filas por vincular dejan sin cubrir para toda la red. */
	public record BlockedDayRow(LocalDate day, int pendingRows) {
	}

	/** De menor a mayor disponibilidad; los «Sin datos» (percent nulo) van al final. */
	public record AvailabilityPointRow(UUID equipmentId, @Schema(nullable = true) String code, String name,
			@Schema(nullable = true) BigDecimal percent, long evaluatedMinutes, long offlineMinutes, int coveredDays) {
	}

	public record AvailabilityExclusion(UUID equipmentId, @Schema(nullable = true) String code, String name,
			ExclusionReason reason) {
	}

	/** Rotulos que acompanan cada cifra: es estimada, es de prueba y de donde salen los datos. */
	public record AvailabilityLabels(boolean estimated, boolean test, String origin) {
	}

	public static AvailabilityResponse from(AvailabilityEstimate result, String timeZone) {
		var estimate = result.estimate();
		return new AvailabilityResponse(
				new AvailabilityPeriod(result.period().preset(), result.period().from(), result.period().to(),
						result.cutOff(), timeZone),
				new AvailabilityAggregate(estimate.aggregate().percent(), estimate.aggregate().evaluatedMinutes(),
						estimate.aggregate().offlineMinutes()),
				new AvailabilityCoverage(estimate.coverage().coveredDays(), estimate.coverage().daysInScope()),
				estimate.blockedDays().stream().map(day -> new BlockedDayRow(day.day(), day.pendingRows())).toList(),
				estimate.points().stream()
						.map(point -> new AvailabilityPointRow(point.equipmentId(), point.code(), point.name(),
								point.percent(), point.evaluatedMinutes(), point.offlineMinutes(), point.coveredDays()))
						.toList(),
				result.exclusions().stream().map(exclusion -> new AvailabilityExclusion(exclusion.equipmentId(),
						exclusion.code(), exclusion.name(), exclusion.reason())).toList(),
				new AvailabilityLabels(true, true, result.origin()), result.limitations());
	}
}
