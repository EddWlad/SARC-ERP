package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.time.Instant;
import java.util.List;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;

/**
 * Contenido unico para los cinco roles: sin observaciones, motivos ni trabajo operativo. Cada apartado
 * es nulo si no hay medicion incluida. {@code trend} conserva como nula la semana sin cifra.
 */
public record UptimeReport(String scope, Instant cutOff, UptimeView.ResolvedPeriod period, Section official,
		Section provisional, List<UptimeView.TrendPoint> trend, List<WeekResult> weeks,
		UptimeCalculator.Coverage coverage, List<UptimeCalculator.Exclusion> exclusions, long pendingLink) {

	public record Section(PeriodType periodType, Instant from, Instant to, UptimeCalculator.Kpis kpis,
			List<UptimeCalculator.ConnectorResult> connectors, List<UptimeCalculator.EquipmentResult> equipment) {
	}

	public record WeekResult(Instant weekStart, UptimeCalculator.Kpis kpis) {
	}
}
