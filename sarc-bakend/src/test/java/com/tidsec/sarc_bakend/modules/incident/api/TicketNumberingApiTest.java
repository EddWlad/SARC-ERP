package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/** Configuracion del numero inicial de tickets (FR-013, FR-014; escenarios 1 a 3). */
class TicketNumberingApiTest extends IncidentApiSupport {

	private static String body(int start, String environment, String confirmation) {
		return "{\"startNumber\":%d,\"environment\":\"%s\",\"confirmation\":\"%s\"}".formatted(start, environment,
				confirmation);
	}

	@Test
	void everyRoleCanReadTheNumberingState() throws Exception {
		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			Cookie session = as(roles.toArray(Role[]::new));
			get(session, "/numbering").andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true))
					.andExpect(jsonPath("$.environment").value("TEST")).andExpect(jsonPath("$.startNumber").value(9001))
					.andExpect(jsonPath("$.nextCode").value("TICKET-9001"))
					.andExpect(jsonPath("$.expectedEnvironment").value("TEST"))
					.andExpect(jsonPath("$.ticketsExist").value(false));
		}
	}

	@Test
	void anUnauthenticatedCallIsRejected() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/numbering"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unconfiguredNumberingReportsItAndHasNoNextCode() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		get(analyst(), "/numbering").andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false))
				.andExpect(jsonPath("$.nextCode").doesNotExist()).andExpect(jsonPath("$.startNumber").doesNotExist())
				.andExpect(jsonPath("$.expectedEnvironment").value("TEST"));
	}

	@Test
	void anAdministratorConfiguresTheStartNumberWithTheConfirmation() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);

		put(admin(), "/numbering", body(9001, "TEST", "TICKET-9001")).andExpect(status().isOk())
				.andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.startNumber").value(9001))
				.andExpect(jsonPath("$.nextCode").value("TICKET-9001")).andExpect(jsonPath("$.environment").value("TEST"));

		assertThat(jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(9001);
		assertThat(jdbc.queryForObject("SELECT configured_by FROM inc_ticket_numbering", java.util.UUID.class))
				.isEqualTo(userId(Role.ADMINISTRADOR));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'NUMBERING_CONFIGURED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void theConfirmationIsComparedIgnoringCaseAndOuterSpaces() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		put(admin(), "/numbering", body(86, "TEST", " ticket-086 ")).andExpect(status().isOk())
				.andExpect(jsonPath("$.nextCode").value("TICKET-086"));
	}

	@Test
	void aWrongConfirmationIsRejectedAndNothingChanges() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		assertProblem(put(admin(), "/numbering", body(9001, "TEST", "TICKET-9002")), 422,
				"TICKET_NUMBERING_CONFIRMATION_MISMATCH");
		assertProblem(put(admin(), "/numbering", body(9001, "TEST", "9001")), 422,
				"TICKET_NUMBERING_CONFIRMATION_MISMATCH");
		assertThat(jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class)).isNull();
	}

	@Test
	void anEnvironmentThatDoesNotMatchTheServerIsRejected() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		assertProblem(put(admin(), "/numbering", body(9001, "PRODUCTION", "TICKET-9001")), 422,
				"TICKET_NUMBERING_ENVIRONMENT_MISMATCH");
		assertThat(jdbc.queryForObject("SELECT environment FROM inc_ticket_numbering", String.class)).isNull();
	}

	@Test
	void invalidInputIsA400() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		assertProblem(put(admin(), "/numbering", body(0, "TEST", "TICKET-000")), 400, "VALIDATION_FAILED");
		assertProblem(put(admin(), "/numbering", body(9001, "OTRO", "TICKET-9001")), 400, "VALIDATION_FAILED");
		assertProblem(put(admin(), "/numbering", "{\"environment\":\"TEST\",\"confirmation\":\"TICKET-9001\"}"), 400,
				"VALIDATION_FAILED");
	}

	@Test
	void theStartNumberIsLockedOnceTicketsExist() throws Exception {
		insertEquipmentTicket(9001);
		assertProblem(put(admin(), "/numbering", body(9500, "TEST", "TICKET-9500")), 409, "TICKET_NUMBERING_LOCKED");
		assertThat(jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class)).isEqualTo(9001);
		get(analyst(), "/numbering").andExpect(jsonPath("$.ticketsExist").value(true));
	}

	@Test
	void anAnalystAndTheReadOnlyRolesCannotConfigureAndNothingChanges() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		java.util.List<Cookie> forbidden = new java.util.ArrayList<>(readOnlyRoles());
		forbidden.add(analyst());
		forbidden.add(as(Role.ANALISTA, Role.GERENTE));
		forbidden.add(secondAnalyst());
		for (Cookie session : forbidden) {
			assertProblem(put(session, "/numbering", body(9001, "TEST", "TICKET-9001")), 403, "ACCESS_DENIED");
		}
		assertThat(jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class)).isNull();
		assertThat(count("audit_event")).isZero();
	}
}
