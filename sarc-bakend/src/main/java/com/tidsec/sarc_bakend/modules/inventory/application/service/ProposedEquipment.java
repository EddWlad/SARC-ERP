package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.List;
import java.util.Objects;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentDetails;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Equipo que propone una fila de la plantilla, con sus valores ya interpretados. Un valor nulo es
 * una celda vacia: en un equipo nuevo falta el dato; en uno existente se conserva el actual (la
 * carga masiva nunca borra datos). Sin conectores = se conservan los actuales.
 */
record ProposedEquipment(ChargingNetwork network, InfrastructureType type, EquipmentBrand brand, String name,
		String serial, String assignedCode, String uidPuk, String location, String mapsUrl, String ocppUrl,
		UsageStatus usageStatus, List<ConnectorType> connectorTypes, List<Identity> identities) {

	/** Identificador del equipo en una plataforma externa, tal como lo guarda el inventario. */
	record Identity(ExternalPlatform platform, String scope, String externalId) {

		/** Como se muestra al analista, p. ej. {@code Enerlink Conectz / ESTACION 4}. */
		String describe() {
			String platformName = switch (platform) {
				case WALLBOX -> "Wallbox";
				case ENERLINK -> "Enerlink";
				case OCPP -> "OCPP";
			};
			return platformName + " " + (scope.isEmpty() ? externalId : scope + " / " + externalId);
		}
	}

	ProposedEquipment {
		connectorTypes = List.copyOf(connectorTypes);
		identities = List.copyOf(identities);
	}

	/** Datos de un equipo nuevo. */
	EquipmentDetails details() {
		return new EquipmentDetails(brand, name, serial, assignedCode, uidPuk, location, mapsUrl, ocppUrl, usageStatus);
	}

	/** Datos con que quedaria {@code current}: las celdas vacias conservan su valor. */
	EquipmentDetails detailsOver(Equipment current) {
		String currentCode = current.classification().usesSerialAsCode() ? null : current.getAssignedCode();
		return new EquipmentDetails(
				Objects.requireNonNullElse(brand, current.getBrand()),
				Objects.requireNonNullElse(name, current.getName()),
				serial,
				assignedCode != null ? assignedCode : currentCode,
				uidPuk != null ? uidPuk : current.getUidPuk(),
				location != null ? location : current.getLocation(),
				mapsUrl != null ? mapsUrl : current.getMapsUrl(),
				ocppUrl != null ? ocppUrl : current.getOcppUrl(),
				usageStatus != null ? usageStatus : current.getUsageStatus());
	}

	/** Tipos de conector por posicion con que quedaria {@code current}. */
	List<ConnectorType> connectorTypesOver(Equipment current) {
		return connectorTypes.isEmpty()
				? current.activeConnectors().stream().map(Connector::getConnectorType).toList()
				: connectorTypes;
	}
}
