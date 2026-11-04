package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScopeConnectorResponse(UUID connectorId, int ordinal, String connectorType, UUID equipmentId,
		String equipmentName, @Schema(nullable = true) String assignedCode) {

	public static ScopeConnectorResponse from(MeasurableConnector connector) {
		return new ScopeConnectorResponse(connector.connectorId(), connector.ordinal(), connector.connectorTypeCode(),
				connector.equipmentId(), connector.equipmentName(), connector.assignedCode());
	}
}
