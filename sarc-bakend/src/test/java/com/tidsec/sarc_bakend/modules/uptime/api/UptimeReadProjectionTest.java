package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Comprueba el JSON serializado: ocultar en Angular no basta (Decision 5). */
class UptimeReadProjectionTest extends UptimeApiSupport {

	private String body(String path) throws Exception {
		return mvc.perform(get("/api/v1/uptime" + path).cookie(manager)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
	}

	@Test
	void pendingValuesNeverReachConsultation() throws Exception {
		registered(JAC_1, "1234", "206");

		for (String path : new String[] { "/summary", "/results/connectors", "/results/equipment", "/measurements" }) {
			assertThat(body(path)).as(path).doesNotContain("85.69").doesNotContain("1234");
		}
	}

	@Test
	void pendingCorrectionKeepsEffectiveValue() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		validate(id);
		send(analyst, "/measurements/" + id + "/corrections", correction(version(id), "1234", "206"));

		String detail = body("/measurements/" + id);
		assertThat(detail).contains("97.29").doesNotContain("1234");
		assertThat(detail).doesNotContain("Dato corregido").doesNotContain("effectiveRevision");
	}

	@Test
	void observationsAndJustificationsAreOperational() throws Exception {
		String id = registered(JAC_1, "1000", "0").get("id").toString();
		send(analyst, "/measurements/" + id + "/validate",
				"{\"version\":%d,\"justification\":\"Motivo reservado\"}".formatted(version(id)));

		String detail = body("/measurements/" + id);
		assertThat(detail).doesNotContain("observations").doesNotContain("Motivo reservado")
				.doesNotContain("SUM_BELOW_DURATION").doesNotContain("excluded");
		assertThat(body("/results/connectors")).doesNotContain("Motivo reservado");
	}

	@Test
	void excludedDisappearsForConsultation() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		validate(id);
		send(analyst, "/measurements/" + id + "/exclude",
				"{\"version\":%d,\"justification\":\"Motivo reservado\"}".formatted(version(id)));

		mvc.perform(get("/api/v1/uptime/measurements/" + id).cookie(manager)).andExpect(status().isNotFound());
		assertThat(body("/measurements")).doesNotContain(id);
		assertThat(body("/results/exclusions")).doesNotContain("EXCLUDED").doesNotContain("Motivo reservado");
		assertThat(mvc.perform(get("/api/v1/uptime/results/exclusions").cookie(analyst)).andReturn().getResponse()
				.getContentAsString()).contains("EXCLUDED");
	}

	@Test
	void exclusionsCarryOnlyConnectorAndState() throws Exception {
		String item = body("/results/exclusions");

		assertThat(item).contains("connectorId").contains("state");
		assertThat(item).doesNotContain("justification").doesNotContain("reason");
	}

	@Test
	void operationsAreNullForConsultation() throws Exception {
		registered(JAC_1, "1000", "0");

		assertThat(body("/summary")).doesNotContain("operations");
		assertThat(mvc.perform(get("/api/v1/uptime/summary").cookie(analyst)).andReturn().getResponse()
				.getContentAsString()).contains("\"observed\":1");
	}
}
