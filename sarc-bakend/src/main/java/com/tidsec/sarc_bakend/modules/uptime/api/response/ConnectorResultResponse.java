package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;

import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Nunca incluye UID/PUK ni URL OCPP. */
public record ConnectorResultResponse(int position, UUID connectorId, int ordinal, String connectorType,
		UUID equipmentId, String equipmentName, @Schema(nullable = true) String assignedCode, ChargingNetwork network,
		InfrastructureType type, @Schema(nullable = true) String brand, String uptimePercent, @Schema(nullable=true) Long evaluatedMinutes,
		@Schema(nullable=true) Long offlineMinutes, @Schema(nullable=true) Long faultMinutes, @Schema(nullable=true) Instant from, @Schema(nullable=true) Instant to,
        @Schema(nullable=true) LocalDate referenceWeek, TemporalPrecision temporalPrecision,String aggregationMethod) {

	public static ConnectorResultResponse from(UptimeCalculator.ConnectorResult result) {
		MeasurableConnector connector = result.connector();
		return new ConnectorResultResponse(result.position(), connector.connectorId(), connector.ordinal(),
				connector.connectorTypeCode(), connector.equipmentId(), connector.equipmentName(),
				connector.assignedCode(), connector.network(), connector.type(), connector.brand(),
				result.uptimePercent().toPlainString(), result.evaluatedMinutes(), result.offlineMinutes(),
				result.faultMinutes(), result.from(), result.to(),result.referenceWeek(),result.temporalPrecision(),result.aggregationMethod());
	}
}
