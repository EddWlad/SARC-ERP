package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import com.tidsec.sarc_bakend.modules.uptime.application.command.UptimeFilter;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.OpeningPeriodPolicy;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Filtros comunes por query string. */
public record UptimeFilterParams(OpeningPeriodPolicy.Kind periodKind, PeriodType periodType,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, ChargingNetwork network, InfrastructureType type,
		String brand, UUID equipmentId, UUID connectorId) {

	public UptimeFilter toFilter() {
		return new UptimeFilter(periodKind, periodType, from, to, network, type, brand, equipmentId, connectorId);
	}
}
