package com.tidsec.sarc_bakend.modules.inventory.application.result;

import java.time.Instant;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Indicadores del inventario (PRD 11.1): totales y distribuciones. Todas las categorias aparecen,
 * aunque valgan cero, para que tableros y reportes muestren el mismo conjunto de valores.
 * Los equipos eliminados no se cuentan salvo en {@code deletedEquipment}.
 */
public record InventorySummary(
		long totalEquipment,
		long deletedEquipment,
		Map<InfrastructureType, Long> byType,
		Map<ChargingNetwork, Long> byNetwork,
		Map<EquipmentBrand, Long> byBrand,
		Map<AdministrativeStatus, Long> byAdminStatus,
		Map<UsageStatus, Long> pointsByUsageStatus,
		Map<String, Long> activeConnectorsByType,
		Instant generatedAt) {
}
