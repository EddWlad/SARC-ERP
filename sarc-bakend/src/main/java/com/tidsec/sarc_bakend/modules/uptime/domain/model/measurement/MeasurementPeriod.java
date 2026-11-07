package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.PeriodRules;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Limites observados y contexto del informe son datos distintos. No deriva unos del otro. */
public record MeasurementPeriod(TemporalPrecision precision, Instant start, Instant end, String zone,
        LocalDate referenceWeek, PeriodType referencePeriodType) {
    public MeasurementPeriod {
        if (precision == null || zone == null || !ZoneId.of(zone).equals(ZoneId.of("America/Guayaquil")))
            throw invalid("Indica la precision y la zona operativa del periodo.");
        if (referenceWeek != null && referenceWeek.getDayOfWeek() != DayOfWeek.MONDAY)
            throw invalid("La semana de referencia debe comenzar en lunes.");
        if ((start == null) != (end == null) || start != null && !end.isAfter(start))
            throw invalid("Indica ambos limites reales, con fin posterior al inicio.");
        switch (precision) {
            case KNOWN_RANGE -> { if (start == null) throw invalid("Indica Desde y Hasta reales del conector."); }
            case REFERENCE_WEEK -> {
                if (start != null || referenceWeek == null || referencePeriodType != PeriodType.ROLLING_7D)
                    throw invalid("El informe sin horario requiere referencia y ventana de siete dias, sin limites reales.");
            }
            case REQUIRES_RECONCILIATION -> { /* Los limites antiguos son solo antecedentes. */ }
        }
    }
    public static MeasurementPeriod known(Instant start, Instant end, String zone, LocalDate reference) {
        return new MeasurementPeriod(TemporalPrecision.KNOWN_RANGE,start,end,zone,reference,
            reference == null ? null : PeriodType.ROLLING_7D);
    }
    public static MeasurementPeriod reference(LocalDate week, String zone) {
        return new MeasurementPeriod(TemporalPrecision.REFERENCE_WEEK,null,null,zone,week,PeriodType.ROLLING_7D);
    }
    public PeriodType effectiveType(PeriodType original) {
        return switch(precision) {
            case KNOWN_RANGE -> original;
            case REFERENCE_WEEK -> PeriodType.ROLLING_7D;
            case REQUIRES_RECONCILIATION -> null;
        };
    }
    public void validate(PeriodType type, boolean exactConfirmed, Instant now) {
        if (precision == TemporalPrecision.KNOWN_RANGE) {
            PeriodRules.check(start,end,type,exactConfirmed,now,ZoneId.of(zone));
        } else if (precision == TemporalPrecision.REFERENCE_WEEK) {
            if (type != PeriodType.ROLLING_7D || exactConfirmed)
                throw new BusinessRuleException("UPTIME_REAL_RANGE_REQUIRED","Sin horario real no se puede confirmar una semana exacta.");
        } else throw new BusinessRuleException("UPTIME_TEMPORAL_RECONCILIATION_REQUIRED",
            "Concilia la procedencia temporal antes de validar esta medicion.");
    }
    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("UPTIME_PERIOD_INVALID",message);
    }
}
