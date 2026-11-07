package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.*;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class MeasurementPeriodTest {
    private static final Instant START = Instant.parse("2026-10-28T13:06:20Z");
    private static final Instant END = Instant.parse("2026-11-04T13:06:20Z");
    private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");
    private static final LocalDate REF = LocalDate.of(2026,10,26);
    private static final String ZONE = "America/Guayaquil";

    @Test void referenceIsNotAnObservedInterval() {
        var p = MeasurementPeriod.reference(REF, ZONE);
        p.validate(PeriodType.ROLLING_7D, false, NOW);
        assertThat(p.start()).isNull(); assertThat(p.end()).isNull();
        assertThat(p.referenceWeek()).isEqualTo(REF);
        assertThat(p.precision()).isEqualTo(TemporalPrecision.REFERENCE_WEEK);
    }
    @Test void captureKeepsSecondsAndIndependentReference() {
        var p=MeasurementPeriod.known(START,END,ZONE,REF);
        p.validate(PeriodType.ROLLING_7D,false,NOW);
        assertThat(p.start()).isEqualTo(START);assertThat(p.end()).isEqualTo(END);
        assertThat(p.referenceWeek()).isEqualTo(REF);
    }
    @Test void rejectsPartialInvertedAndFutureRanges() {
        assertThatThrownBy(()->MeasurementPeriod.known(START,null,ZONE,REF)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->MeasurementPeriod.known(END,START,ZONE,REF)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->MeasurementPeriod.known(START,END,ZONE,REF)
            .validate(PeriodType.ROLLING_7D,false,START)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void referenceMustBeMondayAndCannotBecomeExact() {
        assertThatThrownBy(()->MeasurementPeriod.reference(REF.plusDays(1),ZONE)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->MeasurementPeriod.reference(REF,ZONE)
            .validate(PeriodType.EXACT_WEEK,true,NOW)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void oldKnownRangeDoesNotAcquireReference() {
        assertThat(MeasurementPeriod.known(START,END,ZONE,null).referenceWeek()).isNull();
    }
    @Test void referenceValuesNeedNoDurationAndKeepSourcePrecision() {
        var values=new MeasurementRules.Values(new BigDecimal("9815"),null,BigDecimal.ZERO,
            new BigDecimal("234"),new BigDecimal("97.6800"),UptimeBasis.REPORTED_PERCENT);
        var assessment=MeasurementRules.assess(values,MeasurementPeriod.reference(REF,ZONE),PeriodType.ROLLING_7D);
        assertThat(assessment.validatable()).isTrue();
        assertThat(assessment.occupied()).isNull();
        assertThat(assessment.percent()).isEqualByComparingTo("97.68");
        var longReading=new MeasurementRules.Values(new BigDecimal("20000"),null,BigDecimal.ZERO,
            BigDecimal.ZERO,new BigDecimal("100"),UptimeBasis.REPORTED_PERCENT);
        assertThat(MeasurementRules.assess(longReading,MeasurementPeriod.reference(REF,ZONE),PeriodType.ROLLING_7D)
            .observations()).doesNotContain(Observation.SUM_ABOVE_DURATION,Observation.WINDOW_DURATION_MISMATCH);
    }
    @Test void referenceKeepsAllSourceStatesAndUsesOriginalPercent() {
        var values=new MeasurementRules.Values(new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,new BigDecimal("97.6800"),UptimeBasis.STATES);
        var a=MeasurementRules.assess(values,MeasurementPeriod.reference(REF,ZONE),PeriodType.ROLLING_7D);
        assertThat(a.occupied()).isZero();assertThat(a.basis()).isEqualTo(UptimeBasis.STATES);
        assertThat(a.percent()).isEqualByComparingTo("97.68");
        assertThat(a.observations()).doesNotContain(Observation.PERCENT_MISMATCH,Observation.SUM_BELOW_DURATION);
    }
    @Test void referenceStillRejectsInvalidNumbersForValidation() {
        var bad=new MeasurementRules.Values(new BigDecimal("-1"),null,BigDecimal.ZERO,
            BigDecimal.ONE,new BigDecimal("101"),UptimeBasis.REPORTED_PERCENT);
        var a=MeasurementRules.assess(bad,MeasurementPeriod.reference(REF,ZONE),PeriodType.ROLLING_7D);
        assertThat(a.validatable()).isFalse();
        assertThat(a.observations()).contains(Observation.DURATION_INVALID,Observation.PERCENT_OUT_OF_RANGE);
    }
    @Test void unresolvedPeriodIsNeverEligible() {
        var p=new MeasurementPeriod(TemporalPrecision.REQUIRES_RECONCILIATION,START,END,ZONE,null,null);
        assertThat(p.effectiveType(PeriodType.EXACT_WEEK)).isNull();
        assertThatThrownBy(()->p.validate(PeriodType.EXACT_WEEK,true,NOW)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void temporalChangeKeepsValidatedRevisionAndExclusion() {
        var m=Measurement.open(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),
            MeasurementSource.ENERLINK,START,END,ZONE);
        var data=new RevisionData(ReadingClass.MEASURED,PeriodType.EXACT_WEEK,9815,null,0,234,
            new BigDecimal("97.6800"),java.util.List.of(),null,MeasurementOrigin.FILE,null,
            java.util.Map.of(),"Nombre anterior","Codigo","Tipo2",null,NOW,null,NOW,UptimeBasis.REPORTED_PERCENT);
        var revision=m.register(data);
        m.validate(revision,null,null,NOW,null);m.exclude();
        var previous=revision.getValidatedAt();
        m.changePeriod(MeasurementPeriod.reference(REF,ZONE));
        assertThat(m.getLatestRevision()).isEqualTo(1);
        assertThat(m.getEffectiveRevision()).isEqualTo(1);
        assertThat(m.isExcluded()).isTrue();
        assertThat(revision.getReviewState()).isEqualTo(ReviewState.VALIDATED);
        assertThat(revision.getValidatedAt()).isEqualTo(previous);
        assertThat(revision.getPeriodType()).isEqualTo(PeriodType.EXACT_WEEK);
        assertThat(m.effectivePeriodType(revision.getPeriodType())).isEqualTo(PeriodType.ROLLING_7D);
    }
}
