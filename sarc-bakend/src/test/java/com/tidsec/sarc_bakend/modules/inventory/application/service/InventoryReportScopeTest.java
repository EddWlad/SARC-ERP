package com.tidsec.sarc_bakend.modules.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** El alcance de un reporte se muestra a las personas: usa el vocabulario en espanol, no codigos internos. */
class InventoryReportScopeTest {

	@Test
	void describesTheTypeWithItsSpanishLabel() {
		EquipmentSearchCriteria criteria = new EquipmentSearchCriteria(null, ChargingNetwork.KIA,
				InfrastructureType.CHARGING_STATION, null, null, null);

		assertThat(InventoryReportService.describe(criteria)).isEqualTo("Filtros: red KIA, tipo Electrolinera")
				.doesNotContain("CHARGING_STATION");
	}
}
