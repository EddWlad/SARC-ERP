package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.tidsec.sarc_bakend.support.Feature005Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

class UptimePendingLinkApiTest extends UptimeImportSupport {

	private static final String LABEL = "A- PUESTO 6  - CCS2";
	private static final UUID JAC_3 = Feature005Fixtures.connectorId(Feature005Fixtures.JAC_STATION, 3);

	private String pendingRow() {
		return jdbc.queryForObject("SELECT id::text FROM upt_import_row WHERE outcome = 'PENDING_LINK' LIMIT 1", String.class);
	}

	private long rowVersion(String id) {
		return jdbc.queryForObject("SELECT version FROM upt_import_row WHERE id = ?::uuid", Long.class, id);
	}

	private ResultActions link(String rowId, UUID connector, boolean remember) throws Exception {
		return send(analyst, "/pending-links/" + rowId + "/link", "{\"version\":%d,\"connectorId\":\"%s\",\"rememberLabel\":%s}"
				.formatted(rowVersion(rowId), connector, remember));
	}

	private void importPending(String label) throws Exception {
		confirm(imported("ROLLING_24H", row(label, "1401", "39")), false);
	}

	private String nextPeriod(String label) throws Exception {
		return imported("ROLLING_24H", row(SITE, CHARGER, label, "29-10-2026 14:00:00", "30-10-2026 14:00:00", "1401", "39"));
	}

	@Test
	void initialNumberResolvesWhenThePositionIsActiveAndTheTypeAgrees() throws Exception {
		assertThat(connectorOf(imported("ROLLING_24H", row("2-CCS2", "1401", "39")))).isEqualTo(JAC_2);
	}

	@Test
	void typeThatContradictsThePositionStaysPendingWithTheOnlyConnectorOfThatTypeProposed() throws Exception {
		String id = imported("ROLLING_24H", row("2 - Tipo 2", "1401", "39"));

		assertThat(classification(id)).isEqualTo("PENDING_LINK");
		rows(id).andExpect(jsonPath("$.content[0].suggestedConnectorId").value(JAC_1.toString()))
				.andExpect(jsonPath("$.content[0].connectorId").doesNotExist());
		assertThat(measurements()).isZero();
	}

	@Test
	void inactivePositionStaysPendingWithoutProposalWhenTheLabelNamesNoType() throws Exception {
		String id = imported("ROLLING_24H", row("3", "1401", "39"));

		assertThat(classification(id)).isEqualTo("PENDING_LINK");
		rows(id).andExpect(jsonPath("$.content[0].suggestedConnectorId").doesNotExist());
	}

	@Test
	void proposalIgnoresInactiveConnectorsOfTheSameType() throws Exception {
		String id = imported("ROLLING_24H", row(LABEL, "1401", "39"));

		rows(id).andExpect(jsonPath("$.content[0].suggestedConnectorId").value(JAC_2.toString()));
	}

	@Test
	void noProposalWhenSeveralActiveConnectorsShareTheType() throws Exception {
		identity(Feature005Fixtures.CONECTZ_STATION, "CONECTZ", "CARGADOR CONECTZ", true);

		String id = imported("ROLLING_24H", row("CONECTZ", "CARGADOR CONECTZ", "Conector A - CCS2", FROM, TO, "1401", "39"));

		rows(id).andExpect(jsonPath("$.content[0].suggestedConnectorId").doesNotExist());
	}

	@Test
	void freeTextLabelStaysPendingAndIsListed() throws Exception {
		importPending(LABEL);

		mvc.perform(get("/api/v1/uptime/pending-links").cookie(analyst)).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].sourceConnector").value(LABEL))
				.andExpect(jsonPath("$.content[0].equipmentId").value(Feature005Fixtures.JAC_STATION.toString()));
		assertThat(measurements()).isZero();
	}

	@Test
	void identityMatchesWithoutCaseAccentsOrSpaces() throws Exception {
		String id = imported("ROLLING_24H", row("SEDE   JÁC", "cargador 1", "1", FROM, TO, "1401", "39"));

		assertThat(classification(id)).isEqualTo("NEW");
	}

	@Test
	void ambiguousIdentityStaysPendingWithoutEquipment() throws Exception {
		identity(Feature005Fixtures.JAC_POINT, "Sede doble", "Cargador", true);
		identity(Feature005Fixtures.CONECTZ_STATION, "SEDE DOBLE", "Cargador", true);

		String id = imported("ROLLING_24H", row("Sede doble", "Cargador", "1", FROM, TO, "1401", "39"));

		assertThat(classification(id)).isEqualTo("PENDING_LINK");
		rows(id).andExpect(jsonPath("$.content[0].issues[0].code").value("UPTIME_IDENTITY_AMBIGUOUS"))
				.andExpect(jsonPath("$.content[0].equipmentId").doesNotExist());
	}

	@Test
	void unverifiedIdentityStaysPending() throws Exception {
		identity(Feature005Fixtures.JAC_POINT, "Sede nueva", "Cargador", false);

		assertThat(classification(imported("ROLLING_24H", row("Sede nueva", "Cargador", "1", FROM, TO, "1401", "39"))))
				.isEqualTo("PENDING_LINK");
	}

	@Test
	void linkingCreatesPendingMeasurementWithSourceLabel() throws Exception {
		importPending(LABEL);

		link(pendingRow(), JAC_2, false).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("LINKED"));

		assertThat(jdbc.queryForObject("SELECT equipment_label FROM upt_measurement_revision", String.class)).isEqualTo(CHARGER);
		assertThat(jdbc.queryForObject("SELECT review_state FROM upt_measurement_revision", String.class)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_connector_alias", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'UPTIME_MEASUREMENT_LINKED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void linkingToADifferentMeasurementLeavesItAsConflict() throws Exception {
		importPending(LABEL);
		send(analyst, "/measurements", measurement(JAC_2, "1440", "0")).andExpect(status().isCreated());

		link(pendingRow(), JAC_2, false).andExpect(jsonPath("$.classification").value("CONFLICT"))
				.andExpect(jsonPath("$.outcome").value("SKIPPED"));

		assertThat(jdbc.queryForObject("SELECT latest_revision FROM upt_measurement", Integer.class)).isEqualTo(1);
	}

	@Test
	void staleVersionIsRejected() throws Exception {
		importPending(LABEL);
		String id = pendingRow();

		send(analyst, "/pending-links/" + id + "/link",
				"{\"version\":%d,\"connectorId\":\"%s\"}".formatted(rowVersion(id) + 5, JAC_2))
				.andExpect(status().isConflict());
	}

	@Test
	void connectorOfAnotherEquipmentIsRejected() throws Exception {
		importPending(LABEL);

		link(pendingRow(), Feature005Fixtures.connectorId(Feature005Fixtures.CONECTZ_STATION, 1), false)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_LINK_EQUIPMENT_MISMATCH"));
	}

	@Test
	void aliasNeedsResolvedEquipment() throws Exception {
		confirm(imported("ROLLING_24H", row("Sede rara", "Sin identidad", "1", FROM, TO, "1401", "39")), false);

		link(pendingRow(), JAC_2, true).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_ALIAS_EQUIPMENT_UNRESOLVED"));
	}

	@Test
	void approvedAliasResolvesLaterImportsWithEquivalentWriting() throws Exception {
		importPending(LABEL);
		link(pendingRow(), JAC_2, true).andExpect(status().isOk());

		assertThat(connectorOf(nextPeriod("a- puesto 6 - ccs2"))).isEqualTo(JAC_2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'UPTIME_ALIAS_CREATED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void reassigningAnAliasLeavesMeasurementsAlone() throws Exception {
		importPending(LABEL);
		link(pendingRow(), JAC_2, true).andExpect(status().isOk());

		updateAlias(JAC_1, true).andExpect(status().isOk()).andExpect(jsonPath("$.connectorId").value(JAC_1.toString()));

		assertThat(jdbc.queryForObject("SELECT connector_id FROM upt_measurement", UUID.class)).isEqualTo(JAC_2);
		assertThat(connectorOf(nextPeriod(LABEL))).isEqualTo(JAC_1);
	}

	@Test
	void deactivatedAliasNoLongerResolves() throws Exception {
		importPending(LABEL);
		link(pendingRow(), JAC_2, true).andExpect(status().isOk());

		updateAlias(JAC_2, false).andExpect(status().isOk());

		assertThat(classification(nextPeriod(LABEL))).isEqualTo("PENDING_LINK");
	}

	@Test
	void aliasToInactiveConnectorLeavesTheRowPending() throws Exception {
		importPending(LABEL);
		link(pendingRow(), JAC_2, true).andExpect(status().isOk());
		updateAlias(JAC_3, true).andExpect(status().isOk());

		String id = nextPeriod(LABEL);

		assertThat(classification(id)).isEqualTo("PENDING_LINK");
		rows(id).andExpect(jsonPath("$.content[0].issues[0].code").value("UPTIME_ALIAS_INACTIVE"));
	}

	@Test
	void consultationRolesCannotLink() throws Exception {
		mvc.perform(get("/api/v1/uptime/pending-links").cookie(manager)).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/uptime/connector-aliases").cookie(manager)).andExpect(status().isForbidden());
	}

	private ResultActions updateAlias(UUID connector, boolean active) throws Exception {
		String id = jdbc.queryForObject("SELECT id::text FROM upt_connector_alias", String.class);
		long version = jdbc.queryForObject("SELECT version FROM upt_connector_alias", Long.class);
		return mvc.perform(put("/api/v1/uptime/connector-aliases/" + id).cookie(analyst).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"connectorId\":\"%s\",\"active\":%s}".formatted(version, connector, active)));
	}
}
