package com.tidsec.sarc_bakend.shared.catalog;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Filtros opcionales de conectores. {@code text} busca en nombre, codigo y serie del equipo. */
public record ConnectorQuery(String text, ChargingNetwork network, InfrastructureType type, String brand,
		UUID equipmentId) {

	public static ConnectorQuery all() {
		return new ConnectorQuery(null, null, null, null, null);
	}
}
