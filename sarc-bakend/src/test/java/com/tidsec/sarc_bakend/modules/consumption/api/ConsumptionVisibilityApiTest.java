package com.tidsec.sarc_bakend.modules.consumption.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;

@SpringBootTest(
		properties = {"sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumptionVisibilityApiTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage storage;
	private static final String SUMMARY = "/api/v1/energy/summary?from=2026-10-01&to=2026-11-01";

	@BeforeEach
	void setup() {
		jdbc.execute(
				"TRUNCATE"
					+ " energy_import_row,energy_session_revision,energy_session_review,energy_session,energy_import_batch,asset_external_identity,asset_connector,asset_equipment,report_issued"
					+ " CASCADE");
		Feature004Fixtures.seed(jdbc, false);
	}

	@ParameterizedTest
	@ValueSource(strings = {"GERENTE", "SUPERVISOR", "ASISTENTE"})
	void managersReceiveOfficialOnly(String role) throws Exception {
		mvc.perform(get(SUMMARY).with(user("qa").roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.official.sessions").value(10))
				.andExpect(jsonPath("$.official.energyKwh").value(2240))
				.andExpect(jsonPath("$.approvedAboveThreshold.sessions").value(1))
				.andExpect(jsonPath("$.pendingLink").doesNotExist())
				.andExpect(jsonPath("$.awaitingApproval").doesNotExist())
				.andExpect(jsonPath("$.test").doesNotExist())
				.andExpect(jsonPath("$.energyReview").doesNotExist())
				.andExpect(jsonPath("$.energyRejected").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {"ADMINISTRADOR", "ANALISTA"})
	void operatorsRetainAllFiveCategories(String role) throws Exception {
		mvc.perform(get(SUMMARY).with(user("qa").roles(role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.official.sessions").value(10))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1))
				.andExpect(jsonPath("$.awaitingApproval.sessions").value(1))
				.andExpect(jsonPath("$.test.sessions").value(1))
				.andExpect(jsonPath("$.energyReview.sessions").value(1))
				.andExpect(jsonPath("$.energyRejected.sessions").value(1));
	}

	@Test
	void multiRoleUsesUnionAndRestrictionIsNotZero() throws Exception {
		mvc.perform(get(SUMMARY).with(user("qa").roles("ANALISTA", "GERENTE")))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1));
		mvc.perform(get(SUMMARY).with(user("qa").roles("GERENTE", "SUPERVISOR", "ASISTENTE")))
				.andExpect(jsonPath("$.pendingLink").doesNotExist());
		jdbc.update(
				"delete from energy_session_review where session_id in(select id from"
					+ " energy_session where state<>'VALIDATED')");
		jdbc.update("delete from energy_session where state<>'VALIDATED'");
		mvc.perform(get(SUMMARY).with(user("qa").roles("ANALISTA")))
				.andExpect(jsonPath("$.pendingLink.sessions").value(0));
	}

	@Test
	void readerScopeAppliesBeforePaginationAndFilters() throws Exception {
		mvc.perform(get("/api/v1/energy/sessions?size=2").with(user("qa").roles("GERENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(10));
		mvc.perform(get("/api/v1/energy/sessions?state=TEST").with(user("qa").roles("GERENTE")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CONSUMPTION_OPERATIONAL_ACCESS_DENIED"));
		mvc.perform(
						get("/api/v1/energy/sessions?energyReview=PENDING")
								.with(user("qa").roles("SUPERVISOR")))
				.andExpect(status().isForbidden());
		mvc.perform(
						get("/api/v1/energy/sessions?text=REJECTED")
								.with(user("qa").roles("ASISTENTE")))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void readersSeeCurrentOfficialApprovalNotPreviousRejection() throws Exception {
		UUID id =
				jdbc.queryForObject(
						"select id from energy_session where external_session_id='APPROVED'",
						UUID.class);
		mvc.perform(
						get("/api/v1/energy/sessions/" + id + "/energy-reviews")
								.with(user("qa").roles("GERENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].decision").value("APPROVED"))
				.andExpect(
						jsonPath("$[0].justification").value("QA004 aprobación oficial vigente"));
		mvc.perform(
						get("/api/v1/energy/sessions/" + id + "/energy-reviews")
								.with(user("qa").roles("ANALISTA")))
				.andExpect(jsonPath("$.length()").value(2));
		UUID hidden =
				jdbc.queryForObject(
						"select id from energy_session where external_session_id='REJECTED'",
						UUID.class);
		mvc.perform(
						get("/api/v1/energy/sessions/" + hidden + "/energy-reviews")
								.with(user("qa").roles("GERENTE")))
				.andExpect(status().isNotFound());
		mvc.perform(
						get("/api/v1/energy/sessions/" + UUID.randomUUID() + "/energy-reviews")
								.with(user("qa").roles("GERENTE")))
				.andExpect(status().isNotFound());
	}

	@Test
	void anonymousCannotRead() throws Exception {
		mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
	}
}
