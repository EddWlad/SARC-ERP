package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.time.Instant;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.inventory.application.result.InventorySummary;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Indicadores del tablero de inventario; cada mapa trae todas sus categorias, aunque valgan cero. */
public record InventorySummaryResponse(
		long totalEquipment,
		long deletedEquipment,
		Map<InfrastructureType, Long> byType,
		Map<ChargingNetwork, Long> byNetwork,
		Map<EquipmentBrand, Long> byBrand,
		Map<AdministrativeStatus, Long> byAdminStatus,
		Map<UsageStatus, Long> pointsByUsageStatus,
		Map<String, Long> activeConnectorsByType,
		Instant generatedAt) {

	public static InventorySummaryResponse from(InventorySummary summary) {
		return new InventorySummaryResponse(summary.totalEquipment(), summary.deletedEquipment(), summary.byType(),
				summary.byNetwork(), summary.byBrand(), summary.byAdminStatus(), summary.pointsByUsageStatus(),
				summary.activeConnectorsByType(), summary.generatedAt());
	}
}
