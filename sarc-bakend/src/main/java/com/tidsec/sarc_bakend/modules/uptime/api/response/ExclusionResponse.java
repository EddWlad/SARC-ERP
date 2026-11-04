package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator.UnitState;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

import io.swagger.v3.oas.annotations.media.Schema;

/** Conector y estado, sin motivo. */
public record ExclusionResponse(UUID connectorId, int ordinal, UUID equipmentId, String equipmentName,
		@Schema(nullable = true) String assignedCode, UnitState state) {

	public static ExclusionResponse from(UptimeCalculator.Exclusion exclusion) {
		MeasurableConnector connector = exclusion.connector();
		return new ExclusionResponse(connector.connectorId(), connector.ordinal(), connector.equipmentId(),
				connector.equipmentName(), connector.assignedCode(), exclusion.state());
	}
}
