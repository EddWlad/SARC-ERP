package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.math.BigDecimal;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReferenceReportKpis;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.StateComposition;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;

/** Un mismo calculo para dashboard y reportes. {@code units} son las de la vista que encabeza. */
public record UptimeView(ResolvedPeriod resolved, UptimeCalculator.Kpis official, UptimeCalculator.Kpis provisional,
		UptimeCalculator.Coverage coverage, List<UptimeCalculator.Unit> units, List<TrendPoint> trend,
		List<Notice> notices, long pendingLink, Instant lastValidatedAt, Instant cutOff, Operations operations,
		List<UptimeCalculator.Unit> officialUnits, List<UptimeCalculator.Unit> provisionalUnits, Selection selection, ReferenceReportKpis referenceReport,
        List<UptimeCalculator.Unit> referenceUnits, UptimeCalculator.Coverage provisionalCoverage,
        StateComposition provisionalComposition,long referencePendingLink,long temporalReconciliationCount) {

    public enum SelectionBasis {ACTUAL_RANGE,REFERENCE_WEEK,NONE}
    public record Selection(SelectionBasis basis,LocalDate referenceWeek,PeriodType periodType,String timeZone,
            TemporalPrecision temporalSection) {}

    public UptimeView(ResolvedPeriod resolved,UptimeCalculator.Kpis official,UptimeCalculator.Kpis provisional,
            UptimeCalculator.Coverage coverage,List<UptimeCalculator.Unit> units,List<TrendPoint> trend,
            List<Notice> notices,long pendingLink,Instant lastValidatedAt,Instant cutOff,Operations operations,
            List<UptimeCalculator.Unit> officialUnits,List<UptimeCalculator.Unit> provisionalUnits) {
        this(resolved,official,provisional,coverage,units,trend,notices,pendingLink,lastValidatedAt,cutOff,operations,
            officialUnits,provisionalUnits,new Selection(resolved.from()==null?SelectionBasis.NONE:SelectionBasis.ACTUAL_RANGE,
                null,resolved.periodType(),resolved.timeZone(),null),null,List.of(),
            provisionalUnits.isEmpty()?null:UptimeCalculator.coverage(provisionalUnits),
            provisionalUnits.isEmpty()?null:UptimeCalculator.composition(provisionalUnits),0,0);
    }

	public record ResolvedPeriod(OpeningPeriodPolicy.Kind kind, PeriodType periodType, Instant from, Instant to,
			String timeZone) {
	}

	public record TrendPoint(LocalDate weekStart, BigDecimal uptimePercent) {
	}

	public record Operations(int observed, int readyToValidate, int correctionsPending) {
	}

	public enum Notice {
		NO_OFFICIAL_WEEK,
		LATEST_WEEK_MISSING,
		NO_EVALUABLE_CONNECTORS
	}
}
