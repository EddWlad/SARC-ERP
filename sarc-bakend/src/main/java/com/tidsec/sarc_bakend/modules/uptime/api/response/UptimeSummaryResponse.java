package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.math.BigDecimal;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReferenceReportKpis;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.StateComposition;
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
		@Schema(nullable = true) Kpis provisional, @Schema(nullable=true) Coverage coverage, List<Trend> trend,
		List<UptimeView.Notice> notices, @Schema(nullable = true) Instant lastValidatedAt, Instant cutOff,
		@Schema(nullable = true) UptimeView.Operations operations, Selection selection,
        @Schema(nullable=true) ReferenceReport referenceReport,
        @Schema(nullable=true) Coverage provisionalCoverage,
        @Schema(nullable=true) StateCompositionResponse provisionalComposition,long temporalReconciliationCount) {

    public record ReferenceReport(LocalDate referenceWeek,@Schema(nullable=true) String uptimePercent,
            String aggregationMethod,int equipmentEvaluated,int equipmentWithoutData,int connectorsIncluded,
            int connectorsInScope,Coverage coverage,StateCompositionResponse composition) {
        static ReferenceReport from(ReferenceReportKpis value,long pending) {
            return value==null?null:new ReferenceReport(value.referenceWeek(),text(value.uptimePercent()),"REPORT_MEAN",
                value.equipmentEvaluated(),value.equipmentWithoutData(),value.connectorsIncluded(),value.connectorsInScope(),
                UptimeSummaryResponse.coverage(value.coverage(),pending),StateCompositionResponse.from(value.composition()));
        }
    }

    public record Selection(UptimeView.SelectionBasis basis,@Schema(nullable=true) LocalDate referenceWeek,
            @Schema(nullable=true) PeriodType periodType,String timeZone,
            @Schema(nullable=true) com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision temporalSection) {
        static Selection from(UptimeView.Selection value) {
            return new Selection(value.basis(),value.referenceWeek(),value.periodType(),value.timeZone(),value.temporalSection());
        }
    }

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

		return new UptimeSummaryResponse(
				new ResolvedPeriod(period.kind(), period.periodType(), period.from(), period.to(), period.timeZone()),
				Kpis.from(view.official()), Kpis.from(view.provisional()),
                coverage(view.coverage(), view.pendingLink()),
				view.trend().stream().map(point -> new Trend(point.weekStart(), point.uptimePercent().toPlainString()))
						.toList(),
				view.notices(), view.lastValidatedAt(), view.cutOff(), view.operations(),Selection.from(view.selection()),
                ReferenceReport.from(view.referenceReport(),view.referencePendingLink()),
                coverage(view.provisionalCoverage(),view.pendingLink()),StateCompositionResponse.from(view.provisionalComposition()),view.temporalReconciliationCount());
	}

    private static Coverage coverage(UptimeCalculator.Coverage coverage,long pending) {
        if (coverage==null) return null;
        var states=coverage.byState();
        var byState=new ByState(states.get(UnitState.VALIDATED),states.get(UnitState.PENDING_REVIEW),
            states.get(UnitState.NO_DATA),states.get(UnitState.WITHOUT_MEASUREMENT),states.get(UnitState.OUT_OF_SCOPE),
            states.get(UnitState.RETIRED),states.get(UnitState.EXCLUDED));
        return new Coverage(text(coverage.percent()),coverage.included(),coverage.denominator(),byState,pending);
    }

	private static String text(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}
}
