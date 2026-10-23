package com.tidsec.sarc_bakend.modules.inventory.domain.repository;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Filtros opcionales del inventario (RF-002.6). {@code text} busca en nombre, codigo y serie.
 * Sin {@code adminStatus} se excluyen los eliminados (PRD RN-G-004: no aparecen por defecto).
 */
public record EquipmentSearchCriteria(
		String text,
		ChargingNetwork network,
		InfrastructureType type,
		EquipmentBrand brand,
		AdministrativeStatus adminStatus,
		UsageStatus usageStatus) {

	public static EquipmentSearchCriteria all() {
		return new EquipmentSearchCriteria(null, null, null, null, null, null);
	}
}
