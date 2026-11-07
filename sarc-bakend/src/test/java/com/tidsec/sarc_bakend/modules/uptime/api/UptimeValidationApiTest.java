package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class UptimeValidationApiTest extends UptimeApiSupport {

	private static final String WEEK_START = "2026-10-12T00:00:00-05:00";
	private static final String WEEK_END = "2026-10-19T00:00:00-05:00";

	private String id() throws Exception {
		return registered(JAC_1, "1401", "39").get("id").toString();
	}

	@Test
	void identicalReturnsExistingWithoutWriting() throws Exception {
		String id = id();
		send(analyst, "/measurements", measurement(JAC_1, "1401", "39")).andExpect(status().isOk())
				.andExpect(jsonPath("$.result").value("IDENTICAL")).andExpect(jsonPath("$.id").value(id));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision", Integer.class)).isEqualTo(1);
	}

	@Test
	void differentValuesConflictWithIdAndVersion() throws Exception {
		String id = id();
		send(analyst, "/measurements", measurement(JAC_1, "1400", "40")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("UPTIME_MEASUREMENT_DIFFERS"))
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.version").isNumber());
	}

	@Test
	void twoDifferentNonIntegerOriginalsConflict() throws Exception {
		registered(JAC_1, "9.57", "0");
		send(analyst, "/measurements", measurement(JAC_1, "8.2", "0")).andExpect(status().isConflict());
		send(analyst, "/measurements", measurement(JAC_1, "9.570", "0")).andExpect(status().isOk());
	}

	@Test
	void correctionRequiresJustification() throws Exception {
		String id = id();
		send(analyst, "/measurements/" + id + "/corrections", correction(version(id), "1440", "0")
				.replace("\"justification\":\"Dato corregido\"", "\"justification\":\"\""))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void effectiveStaysUntilCorrectionIsValidated() throws Exception {
		String id = id();
		validate(id);
		send(analyst, "/measurements/" + id + "/corrections", correction(version(id), "1440", "0"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
				.andExpect(jsonPath("$.reviewState").value("PENDING"));

		mvc.perform(get("/api/v1/uptime/measurements/" + id).cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.uptimePercent").value("97.29")).andExpect(jsonPath("$.revision").value(1));

		validate(id);
		mvc.perform(get("/api/v1/uptime/measurements/" + id).cookie(manager))
				.andExpect(jsonPath("$.uptimePercent").value("100.00")).andExpect(jsonPath("$.revision").value(2));
		assertThat(jdbc.queryForObject("SELECT review_state FROM upt_measurement_revision WHERE revision = 1",
				String.class)).isEqualTo("SUPERSEDED");
	}

	@Test
	void alignedWindowBecomesExactWeekOnlyByCorrection() throws Exception {
		String register = measurement(JAC_1, "10080", "0").replace(START, WEEK_START).replace(END, WEEK_END)
				.replace("ROLLING_24H", "ROLLING_7D").replace("\"source\":", "\"referenceWeek\":\"2026-10-12\",\"source\":");
		String created = send(analyst, "/measurements", register).andExpect(status().isCreated())
				.andExpect(jsonPath("$.periodType").value("ROLLING_7D")).andReturn().getResponse().getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

		String correction = correction(version(id), "10080", "0").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true");
		send(analyst, "/measurements/" + id + "/corrections", correction).andExpect(status().isOk())
				.andExpect(jsonPath("$.periodType").value("EXACT_WEEK"));
		assertThat(jdbc.queryForObject("SELECT exact_confirmed_at IS NOT NULL FROM upt_measurement_revision"
				+ " WHERE revision = 2", Boolean.class)).isTrue();
	}

	@Test
	void validatableObservationNeedsJustification() throws Exception {
		String id = registered(JAC_1, "1000", "0").get("id").toString();
		send(analyst, "/measurements/" + id + "/validate", "{\"version\":%d}".formatted(version(id)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_JUSTIFICATION_REQUIRED"));
		send(analyst, "/measurements/" + id + "/validate",
				"{\"version\":%d,\"justification\":\"Corte real\"}".formatted(version(id))).andExpect(status().isOk());
	}

	@Test
	void notValidatableObservationMustBeCorrected() throws Exception {
		String id = registered(JAC_1, "5000", "0").get("id").toString();
		send(analyst, "/measurements/" + id + "/validate",
				"{\"version\":%d,\"justification\":\"x\"}".formatted(version(id)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_NOT_VALIDATABLE"));
	}

	@Test
	void nothingToValidateAndStaleVersion() throws Exception {
		String id = id();
		send(analyst, "/measurements/" + id + "/validate", "{\"version\":99}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		validate(id);
		send(analyst, "/measurements/" + id + "/validate", "{\"version\":%d}".formatted(version(id)))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_NOTHING_TO_VALIDATE"));
	}

	@Test
	void queueFiltersOperationalList() throws Exception {
		String pending = id();
		String observed = registered(JAC_2, "1000", "0").get("id").toString();

		mvc.perform(get("/api/v1/uptime/measurements").cookie(analyst).param("queue", "PENDING"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(pending));
		mvc.perform(get("/api/v1/uptime/measurements").cookie(analyst).param("queue", "OBSERVED"))
				.andExpect(jsonPath("$.content[0].id").value(observed));
	}

	@Test
	void consultationSeesOnlyEffectiveAndCannotUseQueue() throws Exception {
		String validated = id();
		validate(validated);
		String pending = registered(JAC_2, "1440", "0").get("id").toString();

		mvc.perform(get("/api/v1/uptime/measurements").cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(validated))
				.andExpect(jsonPath("$.content[0].observations").doesNotExist());
		mvc.perform(get("/api/v1/uptime/measurements/" + pending).cookie(manager)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/uptime/measurements").cookie(manager).param("queue", "PENDING"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/uptime/measurements/" + validated + "/revisions").cookie(manager))
				.andExpect(status().isForbidden());
	}

	@Test
	void consultationCannotWrite() throws Exception {
		String id = id();
		send(manager, "/measurements/" + id + "/validate", "{\"version\":0}").andExpect(status().isForbidden());
		send(manager, "/measurements/" + id + "/corrections", correction(0, "1440", "0"))
				.andExpect(status().isForbidden());
	}
}
