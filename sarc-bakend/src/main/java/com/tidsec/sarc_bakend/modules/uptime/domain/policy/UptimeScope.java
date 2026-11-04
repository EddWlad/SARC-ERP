package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Infraestructura evaluable (FR-001): KIA solo electrolineras; JAC y CONECTZ ambos tipos. */
public final class UptimeScope {

	private UptimeScope() {
	}

	public static boolean includes(ChargingNetwork network, InfrastructureType type) {
		return network != ChargingNetwork.KIA || type == InfrastructureType.CHARGING_STATION;
	}
}
