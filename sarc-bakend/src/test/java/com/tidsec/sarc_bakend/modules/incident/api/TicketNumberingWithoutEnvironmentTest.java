package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/**
 * Sin la propiedad {@code sarc.incidents.numbering.environment} la aplicacion arranca (por eso esta prueba carga el
 * contexto), pero no se puede configurar la numeracion ni crear tickets (research R2, escenario 47).
 */
@TestPropertySource(properties = "sarc.incidents.numbering.environment=")
class TicketNumberingWithoutEnvironmentTest extends IncidentApiSupport {

	@Test
	void theContextStartsAndTheStateShowsNoExpectedEnvironment() throws Exception {
		get(analyst(), "/numbering").andExpect(status().isOk())
				.andExpect(jsonPath("$.expectedEnvironment").doesNotExist());
	}

	@Test
	void creatingATicketIsRejectedWith409AndNothingIsCreated() throws Exception {
		assertProblem(createTicket(analyst(), equipmentBody(Feature010Fixtures.KIA_STATION, "DISCONNECTION", "x")), 409,
				"TICKET_NUMBERING_ENVIRONMENT_MISMATCH");
		assertThat(count("inc_ticket")).isZero();
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class))
				.as("no consume un numero").isEqualTo(Feature010Fixtures.TEST_START_NUMBER);
	}

	@Test
	void configuringIsRejectedWithAnEnvironmentMismatchWhateverIsSent() throws Exception {
		Feature010Fixtures.clearNumbering(jdbc);
		for (String environment : new String[] { "TEST", "PRODUCTION" }) {
			assertProblem(put(admin(), "/numbering",
					"{\"startNumber\":9001,\"environment\":\"%s\",\"confirmation\":\"TICKET-9001\"}"
							.formatted(environment)),
					422, "TICKET_NUMBERING_ENVIRONMENT_MISMATCH");
		}
		assertThat(jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class)).isNull();
	}
}
