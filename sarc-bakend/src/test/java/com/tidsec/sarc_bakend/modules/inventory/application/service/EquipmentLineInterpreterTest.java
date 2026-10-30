package com.tidsec.sarc_bakend.modules.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.connectortype.ConnectorType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

class EquipmentLineInterpreterTest {

	private static final ConnectorType TYPE2 = ConnectorType.create("TYPE2", "Tipo 2");
	private static final ConnectorType CCS2 = ConnectorType.create("CCS2", "CCS Combo 2");
	private static final Map<String, ConnectorType> CATALOG = Map.of("TYPE2", TYPE2, "CCS2", CCS2);

	@Test
	void readsSpanishLabelsInternalNamesAndCatalogCodesRegardlessOfCaseAndAccents() {
		EquipmentLine line = line("kia", "PUNTO DE CARGA", "wallbox", "Punto Quicentro", "wb-1", "204518", "puk-1",
				"bloqueádo", "type2", null, null, "Conectz", "ESTACION 4", null, "CP-77");

		EquipmentLineInterpreter.Interpretation result = EquipmentLineInterpreter.interpret(line, CATALOG);

		assertThat(result.isValid()).isTrue();
		ProposedEquipment proposal = result.proposal();
		assertThat(proposal.network()).isEqualTo(ChargingNetwork.KIA);
		assertThat(proposal.type()).isEqualTo(InfrastructureType.CHARGING_POINT);
		assertThat(proposal.brand()).isEqualTo(EquipmentBrand.WALLBOX);
		assertThat(proposal.usageStatus()).isEqualTo(UsageStatus.BLOCKED);
		assertThat(proposal.connectorTypes()).containsExactly(TYPE2);
		assertThat(proposal.identities()).containsExactly(
				new ProposedEquipment.Identity(ExternalPlatform.ENERLINK, "Conectz", "ESTACION 4"),
				new ProposedEquipment.Identity(ExternalPlatform.OCPP, "", "CP-77"));
	}

	@Test
	void acceptsInternalNamesAndConnectorCodesWrittenLikeTheCatalogStoresThem() {
		EquipmentLine station = line("CONECTZ", "charging_station", null, null, "S-1", null, null, null, "ccs 2", null,
				null, null, null, null, null);

		ProposedEquipment proposal = EquipmentLineInterpreter.interpret(station, Map.of("CCS_2", CCS2)).proposal();

		assertThat(proposal.type()).isEqualTo(InfrastructureType.CHARGING_STATION);
		assertThat(proposal.connectorTypes()).containsExactly(CCS2);
	}

	@Test
	void reportsEveryFormatProblemOfTheRowAtOnce() {
		EquipmentLine line = line("TESLA", null, "Benny", "X", null, null, null, "Ocupado", "TYPE2", null, "NACS",
				"Conectz", null, null, null);

		EquipmentLineInterpreter.Interpretation result = EquipmentLineInterpreter.interpret(line, CATALOG);

		assertThat(result.proposal()).isNull();
		assertThat(result.issues()).extracting(RowIssue::code).containsExactly("UNKNOWN_VALUE", "VALUE_REQUIRED",
				"UNKNOWN_VALUE", "SERIAL_REQUIRED", "CONNECTORS_NOT_IN_ORDER", "ENERLINK_CHARGER_REQUIRED");
		assertThat(result.issues().getFirst().message()).contains("TESLA").contains("KIA, JAC, CONECTZ");
	}

	@Test
	void unknownConnectorCodesAreNamed() {
		EquipmentLineInterpreter.Interpretation result = EquipmentLineInterpreter.interpret(line("JAC", "Electrolinera",
				null, null, "S-1", null, null, null, "TYPE2", "NACS", "!!", null, null, null, null), CATALOG);

		assertThat(result.issues()).extracting(RowIssue::message).containsExactly(
				"El tipo de conector \"NACS\" no existe en el catalogo",
				"El tipo de conector \"!!\" no existe en el catalogo");
	}

	@Test
	void theEnerlinkPairMustComeCompleteOrNotAtAll() {
		assertThat(enerlinkIssues(null, "JAC PUMA")).containsExactly("ENERLINK_SCOPE_REQUIRED");
		assertThat(enerlinkIssues("JAC PUMA", null)).containsExactly("ENERLINK_CHARGER_REQUIRED");
		assertThat(enerlinkIssues(null, null)).isEmpty();
		assertThat(EquipmentLineInterpreter.interpret(enerlinkLine(null, null), CATALOG).proposal().identities())
				.isEmpty();
	}

	private static java.util.List<String> enerlinkIssues(String scope, String charger) {
		return EquipmentLineInterpreter.interpret(enerlinkLine(scope, charger), CATALOG).issues().stream()
				.map(RowIssue::code).toList();
	}

	private static EquipmentLine enerlinkLine(String scope, String charger) {
		return line("JAC", "Electrolinera", null, null, "S-1", null, null, null, "TYPE2", null, null, scope, charger,
				null, null);
	}

	private static EquipmentLine line(String network, String type, String brand, String name, String serial,
			String code, String uid, String usage, String connector1, String connector2, String connector3,
			String enerlinkScope, String enerlinkCharger, String wallbox, String ocpp) {
		return new EquipmentLine(network, type, brand, name, serial, code, uid, null, null, null, usage, connector1,
				connector2, connector3, enerlinkScope, enerlinkCharger, wallbox, ocpp);
	}
}
