package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.result.GridRow;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

import io.swagger.v3.oas.annotations.media.Schema;

public record GridRowResponse(UUID connectorId, int ordinal, String connectorTypeCode, String connectorTypeName,
		boolean connectorActive, UUID equipmentId, String equipmentName,
		@Schema(nullable = true) String assignedCode, ChargingNetwork network, InfrastructureType type, String brand,
		AdministrativeStatus equipmentStatus, GridRow.Situation situation,
		@Schema(nullable = true) UUID measurementId, @Schema(nullable = true) Long version, boolean referenceReportPresent) {

	public static GridRowResponse from(GridRow row) {
		MeasurableConnector connector = row.connector();
		return new GridRowResponse(connector.connectorId(), connector.ordinal(), connector.connectorTypeCode(),
				connector.connectorTypeName(), connector.connectorActive(), connector.equipmentId(),
				connector.equipmentName(), connector.assignedCode(), connector.network(), connector.type(),
				connector.brand(), connector.equipmentStatus(), row.situation(), row.measurementId(), row.version(), row.referenceReportPresent());
	}
}
