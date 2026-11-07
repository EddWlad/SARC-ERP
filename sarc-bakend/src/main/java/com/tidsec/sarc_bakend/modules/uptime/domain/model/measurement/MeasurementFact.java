package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Proyeccion de lectura. Los minutos de calculo conocido no sustituyen los estados fuente. */
public record MeasurementFact(UUID measurementId,UUID connectorId,UUID equipmentId,Instant periodStart,
        Instant periodEnd,PeriodType periodType,ReadingClass readingClass,boolean validated,boolean excluded,
        Long evaluatedMinutes,Long offlineMinutes,Long faultMinutes,boolean latestPending,boolean latestObserved,
        BigDecimal downMinutes,TemporalPrecision temporalPrecision,LocalDate referenceWeek,BigDecimal reportedPercent,
        Long availableSourceMinutes,Long occupiedSourceMinutes,Long faultSourceMinutes,Long offlineSourceMinutes,
        TemporalPrecision pendingSection) {
    /** Compatibilidad de hechos conocidos y fixtures anteriores; no inventa estados fuente. */
    public MeasurementFact(UUID id,UUID connector,UUID equipment,Instant start,Instant end,PeriodType type,
            ReadingClass kind,boolean validated,boolean excluded,long evaluated,long offline,long fault,
            boolean pending,boolean observed,BigDecimal down) {
        this(id,connector,equipment,start,end,type,kind,validated,excluded,evaluated,offline,fault,pending,observed,
            down,TemporalPrecision.KNOWN_RANGE,null,null,null,null,null,null,null);
    }
    public static MeasurementFact reference(UUID id,UUID connector,UUID equipment,LocalDate week,
            BigDecimal percent,Long available,Long occupied,Long fault,Long offline,boolean validated,boolean excluded) {
        return new MeasurementFact(id,connector,equipment,null,null,PeriodType.ROLLING_7D,ReadingClass.MEASURED,
            validated,excluded,null,offline,fault,!validated,false,null,TemporalPrecision.REFERENCE_WEEK,
            week,percent,available,occupied,fault,offline,null);
    }
    public boolean included() {
        if (!validated || excluded || readingClass!=ReadingClass.MEASURED
                || temporalPrecision==TemporalPrecision.REQUIRES_RECONCILIATION) return false;
        if (temporalPrecision==TemporalPrecision.REFERENCE_WEEK)
            return reportedPercent!=null && reportedPercent.signum()>=0
                && reportedPercent.compareTo(BigDecimal.valueOf(100))<=0;
        return evaluatedMinutes!=null && evaluatedMinutes>0;
    }
    public boolean belongsTo(TemporalPrecision section) {
        return temporalPrecision==section || temporalPrecision==TemporalPrecision.REQUIRES_RECONCILIATION
            && pendingSection==section;
    }
}
