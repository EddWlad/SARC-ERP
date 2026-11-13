package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Historial de ventanas: filtros, paginacion y la proyeccion por rol de FR-005. */
class WindowListApiTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;

	@Test
	void anEditorFiltersByPointAndStateAndSeesProvisionalMinutesOfOpenWindows() throws Exception {
		insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		insertWindow(POINT, at("10:00"), at("15:00"), "REVIEW");
		insertWindow(POINT, at("16:01"), null, "OPEN");
		insertWindow(Feature008Fixtures.FREE, at("08:00"), at("09:00"), "CONFIRMED");

		get(analyst(), "/windows?equipmentIds=" + POINT).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content", hasSize(3)))
				.andExpect(jsonPath("$.content[0].state").value("OPEN"))
				.andExpect(jsonPath("$.content[0].provisionalMinutes").isNumber())
				.andExpect(jsonPath("$.content[0].durationMinutes").doesNotExist())
				.andExpect(jsonPath("$.content[1].reviewReasons[0]").value("LONG_WINDOW"));
		get(analyst(), "/windows?equipmentIds=" + POINT + "&states=OPEN").andExpect(jsonPath("$.content", hasSize(1)))
				.andExpect(jsonPath("$.content[0].state").value("OPEN"));
		get(analyst(), "/windows?states=CONFIRMED").andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void filtersByRangeWithAnExclusiveEndAndPaginates() throws Exception {
		insertWindow(POINT, at("2026-10-14", "23:00"), at("2026-10-15", "01:00"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-15", "10:00"), at("2026-10-15", "11:00"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-16", "00:00"), at("2026-10-16", "01:00"), "CONFIRMED");

		get(analyst(), "/windows?from=2026-10-15&to=2026-10-16").andExpect(jsonPath("$.totalElements").value(2));
		get(analyst(), "/windows?from=2026-10-16&to=2026-10-17").andExpect(jsonPath("$.totalElements").value(1));
		get(analyst(), "/windows?size=2").andExpect(jsonPath("$.content", hasSize(2)))
				.andExpect(jsonPath("$.totalPages").value(2));
		get(analyst(), "/windows?size=0").andExpect(status().isBadRequest());
	}

	@Test
	void readOnlyRolesSeeOnlyConfirmedWindowsWhateverTheyAsk() throws Exception {
		insertWindow(POINT, at("08:00"), at("09:00"), "CONFIRMED");
		insertWindow(POINT, at("10:00"), at("15:00"), "REVIEW");
		insertWindow(POINT, at("16:01"), null, "OPEN");
		insertWindow(Feature008Fixtures.FREE, at("08:00"), at("09:00"), "TEST");

		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			get(as(role), "/windows?states=OPEN&states=REVIEW&states=TEST").andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElements").value(1))
					.andExpect(jsonPath("$.content[0].state").value("CONFIRMED"))
					.andExpect(jsonPath("$.content[0].reviewReasons", hasSize(0)));
		}
		get(as(Role.ANALISTA, Role.GERENTE), "/windows").andExpect(jsonPath("$.totalElements").value(4));
	}
}
