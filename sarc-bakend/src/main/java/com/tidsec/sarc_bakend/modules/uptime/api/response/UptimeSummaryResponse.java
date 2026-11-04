package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.UnitState;

import io.swagger.v3.oas.annotations.media.Schema;

/** Porcentajes y horas viajan como texto decimal: dashboard, PDF y Excel presentan el mismo valor. */
public record UptimeSummaryResponse(ResolvedPeriod resolvedPeriod, @Schema(nullable = true) Kpis official,
		@Schema(nullable = true) Kpis provisional, Coverage coverage, List<Trend> trend,
		List<UptimeView.Notice> notices, @Schema(nullable = true) Instant lastValidatedAt, Instant cutOff,
		@Schema(nullable = true) UptimeView.Operations operations) {

	public record ResolvedPeriod(OpeningPeriodPolicy.Kind kind, @Schema(nullable = true) PeriodType periodType,
			@Schema(nullable = true) Instant from, @Schema(nullable = true) Instant to, String timeZone) {
	}

	public record Kpis(String uptimePercent, long evaluatedMinutes, String offlineHours, String faultHours,
			int equipmentEvaluated, int equipmentWithoutData, int connectorsIncluded, int connectorsInScope) {

		static Kpis from(UptimeCalculator.Kpis kpis) {
			return kpis == null ? null
					: new Kpis(kpis.uptimePercent().toPlainString(), kpis.evaluatedMinutes(),
							kpis.offlineHours().toPlainString(), kpis.faultHours().toPlainString(),
							kpis.equipmentEvaluated(), kpis.equipmentWithoutData(), kpis.connectorsIncluded(),
							kpis.connectorsInScope());
		}
	}

	public record Coverage(@Schema(nullable = true) String percent, int included, int denominator, ByState byState,
			long pendingLink) {
	}

	public record ByState(int validated, int pendingReview, int noData, int withoutMeasurement, int outOfScope,
			int retired, int excluded) {
	}

	public record Trend(LocalDate weekStart, String uptimePercent) {
	}

	public static UptimeSummaryResponse from(UptimeView view) {
		UptimeView.ResolvedPeriod period = view.resolved();
		UptimeCalculator.Coverage coverage = view.coverage();
		var states = coverage.byState();
		ByState byState = new ByState(states.get(UnitState.VALIDATED), states.get(UnitState.PENDING_REVIEW),
				states.get(UnitState.NO_DATA), states.get(UnitState.WITHOUT_MEASUREMENT),
				states.get(UnitState.OUT_OF_SCOPE), states.get(UnitState.RETIRED), states.get(UnitState.EXCLUDED));
		return new UptimeSummaryResponse(
				new ResolvedPeriod(period.kind(), period.periodType(), period.from(), period.to(), period.timeZone()),
				Kpis.from(view.official()), Kpis.from(view.provisional()),
				new Coverage(text(coverage.percent()), coverage.included(), coverage.denominator(), byState,
						view.pendingLink()),
				view.trend().stream().map(point -> new Trend(point.weekStart(), point.uptimePercent().toPlainString()))
						.toList(),
				view.notices(), view.lastValidatedAt(), view.cutOff(), view.operations());
	}

	private static String text(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}
}
