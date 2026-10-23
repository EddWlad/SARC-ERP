package com.tidsec.sarc_bakend.modules.consumption.domain.policy;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Fuente preferente por red y tipo (PRD 7.2): puntos KIA por Wallbox; electrolineras KIA y todo
 * JAC y CONECTZ por Enerlink. Es una preferencia operativa, no una relacion fija con la marca: una
 * sesion de otra fuente no se descarta, pero se pide aprobarla para no contar dos veces la misma
 * carga si ambas plataformas la reportan.
 */
public final class PreferredSourcePolicy {

	private PreferredSourcePolicy() {
	}

	public static SessionSource preferredFor(ChargingNetwork network, InfrastructureType type) {
		if (network == ChargingNetwork.KIA && type == InfrastructureType.CHARGING_POINT) {
			return SessionSource.WALLBOX;
		}
		return SessionSource.ENERLINK;
	}

	public static boolean isPreferred(SessionSource source, ChargingNetwork network, InfrastructureType type) {
		return preferredFor(network, type) == source;
	}
}
