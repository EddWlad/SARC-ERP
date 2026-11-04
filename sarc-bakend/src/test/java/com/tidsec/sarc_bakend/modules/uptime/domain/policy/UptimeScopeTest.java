package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

class UptimeScopeTest {

	@Test
	void kiaOnlyMeasuresStations() {
		assertThat(UptimeScope.includes(ChargingNetwork.KIA, InfrastructureType.CHARGING_STATION)).isTrue();
		assertThat(UptimeScope.includes(ChargingNetwork.KIA, InfrastructureType.CHARGING_POINT)).isFalse();
	}

	@Test
	void jacAndConectzMeasureBothTypes() {
		for (ChargingNetwork network : new ChargingNetwork[] { ChargingNetwork.JAC, ChargingNetwork.CONECTZ }) {
			for (InfrastructureType type : InfrastructureType.values()) {
				assertThat(UptimeScope.includes(network, type)).isTrue();
			}
		}
	}
}
