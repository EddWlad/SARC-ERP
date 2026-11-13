package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Lectura del historial por rol (FR-005): quien no edita solo ve ventanas confirmadas, sin historial ni procedencia. */
class WindowHistoryReadTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;

	@Test
	void aReadOnlyUserSeesTheConfirmedDetailWithoutRevisionsOrProvenance() throws Exception {
		UUID confirmed = insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		jdbc.update("UPDATE itm_window SET source = 'XLSX' WHERE id = ?", confirmed);

		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			get(as(role), "/windows/" + confirmed).andExpect(status().isOk())
					.andExpect(jsonPath("$.window.state").value("CONFIRMED"))
					.andExpect(jsonPath("$.window.durationMinutes").value(60))
					.andExpect(jsonPath("$.window.reviewReasons", hasSize(0)))
					.andExpect(jsonPath("$.revisions").doesNotExist()).andExpect(jsonPath("$.batchId").doesNotExist())
					.andExpect(jsonPath("$.rowNumber").doesNotExist());
		}
	}

	@Test
	void aReadOnlyUserGetsTheSameNotFoundForANonConfirmedWindowAsForAMissingOne() throws Exception {
		UUID open = insertWindow(POINT, at("08:00"), null, "OPEN");
		UUID review = insertWindow(Feature008Fixtures.FREE, at("08:00"), at("13:00"), "REVIEW");
		UUID test = insertWindow(Feature008Fixtures.FREE, at("14:00"), at("14:30"), "TEST");
		UUID annulled = insertWindow(Feature008Fixtures.FREE, at("15:00"), at("15:30"), "ANNULLED");
		String missing = get(as(Role.GERENTE), "/windows/" + UUID.randomUUID()).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		for (UUID id : new UUID[] { open, review, test, annulled }) {
			String body = get(as(Role.GERENTE), "/windows/" + id).andExpect(status().isNotFound()).andReturn()
					.getResponse().getContentAsString();
			assertThat((String) JsonPath.read(body, "$.code")).isEqualTo(JsonPath.read(missing, "$.code"));
			assertThat((String) JsonPath.read(body, "$.detail")).isEqualTo(JsonPath.read(missing, "$.detail"));
		}
	}

	@Test
	void anEditorSeesEveryStateWithItsRevisionsAndProvenance() throws Exception {
		for (String state : new String[] { "OPEN", "REVIEW", "TEST", "ANNULLED" }) {
			String start = switch (state) {
				case "OPEN" -> "08:00";
				case "REVIEW" -> "09:00";
				case "TEST" -> "15:00";
				default -> "16:00";
			};
			String end = state.equals("OPEN") ? null : state.equals("REVIEW") ? at("14:00")
					: at(state.equals("TEST") ? "15:30" : "16:30");
			UUID id = insertWindow(state.equals("OPEN") ? Feature008Fixtures.SECOND_BLOCKED : POINT, at(start), end, state);
			get(analyst(), "/windows/" + id).andExpect(status().isOk())
					.andExpect(jsonPath("$.window.state").value(state)).andExpect(jsonPath("$.revisions", hasSize(1)))
					.andExpect(jsonPath("$.revisions[0].change").value("CREATED"))
					.andExpect(jsonPath("$.currentPointName").value(state.equals("OPEN") ? "QA008 Punto Bloqueado Dos"
							: "QA008 Punto Bloqueado"));
		}
		get(as(Role.ANALISTA, Role.GERENTE), "/windows").andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4));
	}
}
