package com.tidsec.sarc_bakend.modules.consumption.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EquipmentEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

class ConsumptionCalculatorTest {
	@Test
	void ranksAllTiedWithTheFifthByEnergyAndUsesSharedPositions() {
		for (List<String> energies : List.of(List.of("100", "90", "80", "70", "70.000", "70", "60"),
				List.of("100", "90", "80", "70", "60", "60", "60"), List.of("0", "0", "0", "0", "0", "0"),
				List.of("100", "90", "90.000", "70", "60", "50"), List.of("3", "2", "1"),
				List.of("1.005", "1.004", "1.003", "1.002", "1.001", "1.000"))) {
			var sessions = mock(EnergySessionRepository.class);
			var directory = mock(EquipmentDirectory.class);
			var rows = new ArrayList<EquipmentEnergy>();
			var equipment = new HashMap<UUID, EquipmentReference>();
			for (int i = 0; i < energies.size(); i++) {
				UUID id = UUID.randomUUID();
				rows.add(new EquipmentEnergy(id, 1, new BigDecimal(energies.get(i))));
				equipment.put(id, new EquipmentReference(id, ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
						"Equipo " + i, "S" + i));
			}
			java.util.Collections.reverse(rows);
			when(sessions.byEquipment(any())).thenReturn(rows);
			when(directory.findByIds(any())).thenReturn(equipment);
			var calculator = new ConsumptionCalculator(sessions, directory, Clock.systemUTC());
			var all = calculator.ranking(new SessionCriteria(null, null, null, null, null, null, null, null), 0);
			var top = calculator.ranking(new SessionCriteria(null, null, null, null, null, null, null, null), 5);
			if (energies.getFirst().equals("100") && energies.get(3).equals("70") && energies.get(4).equals("70.000")) {
				assertThat(all).extracting(item -> item.position()).containsExactly(1, 2, 3, 4, 4, 4, 7);
				assertThat(top).hasSize(6);
			}
			else if (energies.getFirst().equals("0") || energies.size() < 6 || energies.get(4).equals("60") && energies.get(5).equals("60")) {
				assertThat(top).hasSize(energies.size());
			}
			else {
				assertThat(top).hasSize(5);
			}
			assertThat(all).extracting(item -> item.equipment().name()).isSorted();
			if (energies.getFirst().equals("1.005")) {
				assertThat(all).extracting(item -> item.position()).containsExactly(1, 2, 3, 4, 5, 6);
			}
			if (energies.size() > 2 && energies.get(2).equals("90.000")) {
				assertThat(all).extracting(item -> item.position()).containsExactly(1, 2, 2, 4, 5, 6);
			}
		}
	}

	@Test
	void tiedNamesRemainStableAndNoUuidCanExcludeATiedMember() {
		var sessions = mock(EnergySessionRepository.class);
		var directory = mock(EquipmentDirectory.class);
		var rows = new ArrayList<EquipmentEnergy>();
		var equipment = new HashMap<UUID, EquipmentReference>();
		for (int i = 6; i >= 0; i--) {
			UUID id = new UUID(0, i + 1);
			rows.add(new EquipmentEnergy(id, 1, new BigDecimal(i % 2 == 0 ? "0.000" : "0")));
			equipment.put(id, new EquipmentReference(id, ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
					i < 3 ? "A" : "B", null));
		}
		when(sessions.byEquipment(any())).thenReturn(rows);
		when(directory.findByIds(any())).thenReturn(equipment);
		var top = new ConsumptionCalculator(sessions, directory, Clock.systemUTC())
				.ranking(new SessionCriteria(null, null, null, null, null, null, null, null), 5);
		assertThat(top).hasSize(7).allSatisfy(item -> assertThat(item.position()).isEqualTo(1));
		assertThat(top).extracting(item -> item.equipment().id()).isSorted();
	}
}
