package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.support.Feature005Fixtures;

class UptimeClassificationApiTest extends UptimeApiSupport {

	private static String classification(String connector, String justification) {
		return """
				{"equipmentId":"%s","connectorId":%s,"readingClass":"NO_DATA","source":"ENERLINK","periodStart":"%s",
				 "periodEnd":"%s","periodType":"ROLLING_24H","exactConfirmed":false,"justification":%s}
				""".formatted(Feature005Fixtures.JAC_STATION, connector, START, END, justification);
	}

	@Test
	void equipmentMarkCreatesOneMeasurementPerConnectorInScope() throws Exception {
		send(analyst, "/measurements/classifications", classification("null", "\"Sin reporte\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].result").value("CREATED"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision WHERE reading_class = 'NO_DATA'"
				+ " AND available_minutes IS NULL", Integer.class)).isEqualTo(2);
	}

	@Test
	void singleConnectorMark() throws Exception {
		send(analyst, "/measurements/classifications", classification("\"" + JAC_2 + "\"", "\"Sin reporte\""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void justificationIsMandatory() throws Exception {
		send(analyst, "/measurements/classifications", classification("null", "\"\""))
				.andExpect(status().is4xxClientError());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Integer.class)).isZero();
	}

	@Test
	void existingDifferentMeasurementIsReportedNotOverwritten() throws Exception {
		registered(JAC_1, "1401", "39");

		send(analyst, "/measurements/classifications", classification("null", "\"Sin reporte\""))
				.andExpect(jsonPath("$[?(@.connectorId=='%s')].result".formatted(JAC_1)).value("DIFFERS"))
				.andExpect(jsonPath("$[?(@.connectorId=='%s')].result".formatted(JAC_2)).value("CREATED"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision WHERE reading_class = 'MEASURED'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void exclusionIsReversibleAndNeverDeletes() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		validate(id);

		send(analyst, "/measurements/" + id + "/exclude",
				"{\"version\":%d,\"justification\":\"Cargador en obra\"}".formatted(version(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.excluded").value(true));
		send(analyst, "/measurements/" + id + "/restore",
				"{\"version\":%d,\"justification\":\"Fin de obra\"}".formatted(version(id)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.excluded").value(false));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_exclusion", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement_revision", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action IN"
				+ " ('UPTIME_MEASUREMENT_EXCLUDED', 'UPTIME_MEASUREMENT_RESTORED')", Integer.class)).isEqualTo(2);
	}

	@Test
	void exclusionNeedsJustification() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		send(analyst, "/measurements/" + id + "/exclude", "{\"version\":%d,\"justification\":\" \"}".formatted(version(id)))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void correctionAuditKeepsPreviousAndNewValues() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		send(analyst, "/measurements/" + id + "/corrections", correction(version(id), "1440", "0"))
				.andExpect(status().isOk());

		String details = jdbc.queryForObject(
				"SELECT details::text FROM audit_event WHERE action = 'UPTIME_MEASUREMENT_CORRECTED'", String.class);
		assertThat(details).contains("previous").contains("39").contains("current");
	}
}
