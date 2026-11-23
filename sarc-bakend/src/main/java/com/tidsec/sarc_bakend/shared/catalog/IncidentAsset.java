package com.tidsec.sarc_bakend.shared.catalog;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Lo que Incidencias puede saber de un equipo del inventario (feature 010). Nunca incluye UID/PUK, URL OCPP ni
 * serie (RNF-SEG-07). {@code location} y {@code mapsUrl} pueden ser nulos: no bloquean el registro (FR-006).
 */
public record IncidentAsset(UUID id, String name, String assignedCode, ChargingNetwork network,
		InfrastructureType infrastructureType, String location, String mapsUrl, AdministrativeStatus adminStatus,
		List<IncidentConnector> connectors) {

	public IncidentAsset {
		connectors = connectors == null ? List.of() : List.copyOf(connectors);
	}

	public boolean active() {
		return adminStatus == AdministrativeStatus.ACTIVE;
	}
}
