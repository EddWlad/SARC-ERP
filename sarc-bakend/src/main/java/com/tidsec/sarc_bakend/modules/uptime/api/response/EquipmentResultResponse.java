package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Promedio de conectores ponderado por minutos. Sin medicion incluida el porcentaje es nulo. */
public record EquipmentResultResponse(UUID equipmentId, String equipmentName,
		@Schema(nullable = true) String assignedCode, ChargingNetwork network, InfrastructureType type,
		@Schema(nullable = true) String uptimePercent, long evaluatedMinutes, long offlineMinutes, long faultMinutes,
		int connectorsIncluded, int connectorsInScope) {

	public static EquipmentResultResponse from(UptimeCalculator.EquipmentResult result) {
		MeasurableConnector sample = result.sample();
		return new EquipmentResultResponse(sample.equipmentId(), sample.equipmentName(), sample.assignedCode(),
				sample.network(), sample.type(),
				result.uptimePercent() == null ? null : result.uptimePercent().toPlainString(),
				result.evaluatedMinutes(), result.offlineMinutes(), result.faultMinutes(), result.connectorsIncluded(),
				result.connectorsInScope());
	}
}
