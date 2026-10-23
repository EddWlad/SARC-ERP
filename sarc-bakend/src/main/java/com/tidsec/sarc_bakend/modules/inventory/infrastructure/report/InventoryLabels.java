package com.tidsec.sarc_bakend.modules.inventory.infrastructure.report;

import java.util.List;

import com.tidsec.sarc_bakend.modules.inventory.application.result.ConnectorView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Textos en espanol de las categorias del inventario para los reportes. */
final class InventoryLabels {

	private InventoryLabels() {
	}

	static String connectorCodes(List<ConnectorView> connectors) {
		return String.join(", ", connectors.stream().map(ConnectorView::connectorTypeCode).toList());
	}

	static String of(EquipmentBrand brand) {
		return switch (brand) {
			case BENNY -> "Benny";
			case WALLBOX -> "Wallbox";
		};
	}

	static String of(AdministrativeStatus status) {
		return switch (status) {
			case ACTIVE -> "Activo";
			case INACTIVE -> "Inactivo";
			case DELETED -> "Eliminado";
		};
	}

	static String of(UsageStatus status) {
		if (status == null) {
			return "No aplica";
		}
		return switch (status) {
			case FREE -> "Libre";
			case BLOCKED -> "Bloqueado";
			case DISCONNECTED -> "Desconectado";
		};
	}
}
