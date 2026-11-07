package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;

/** Resultado del informe sin horario: no contiene minutos evaluados ficticios. */
public record ReferenceReportKpis(LocalDate referenceWeek,BigDecimal uptimePercent,int equipmentEvaluated,
        int equipmentWithoutData,int connectorsIncluded,int connectorsInScope,
        UptimeCalculator.Coverage coverage,StateComposition composition) {}
