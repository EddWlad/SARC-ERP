package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Filtros comunes de indicadores y reportes. {@code to} es exclusivo; sin {@code periodKind}, apertura. */
public record UptimeFilter(OpeningPeriodPolicy.Kind periodKind, PeriodType periodType, LocalDate from, LocalDate to,
		ChargingNetwork network, InfrastructureType type, String brand, UUID equipmentId, UUID connectorId, LocalDate referenceWeek, TemporalPrecision temporalSection) {
    public UptimeFilter(OpeningPeriodPolicy.Kind kind,PeriodType periodType,LocalDate from,LocalDate to,
            ChargingNetwork network,InfrastructureType type,String brand,UUID equipmentId,UUID connectorId) {
        this(kind,periodType,from,to,network,type,brand,equipmentId,connectorId,null,null);
    }
}

