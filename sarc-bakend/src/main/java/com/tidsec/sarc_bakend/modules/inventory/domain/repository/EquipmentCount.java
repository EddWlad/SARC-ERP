package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Cantidad de equipos por combinacion de dimensiones; base de los indicadores del inventario. */
public record EquipmentCount(
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentBrand brand,
		AdministrativeStatus adminStatus,
		UsageStatus usageStatus,
		long count) {
}
