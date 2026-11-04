package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Filtros comunes de indicadores y reportes. {@code to} es exclusivo; sin {@code periodKind}, apertura. */
public record UptimeFilter(OpeningPeriodPolicy.Kind periodKind, PeriodType periodType, LocalDate from, LocalDate to,
		ChargingNetwork network, InfrastructureType type, String brand, UUID equipmentId, UUID connectorId) {
}
