package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.support.Feature005Fixtures;

class UptimeSummaryApiTest extends UptimeApiSupport {

	private static final String SUMMARY = "/api/v1/uptime/summary";

	private String official(UUID connector, String monday, String nextMonday, String available, String offline)
			throws Exception {
		String body = measurement(connector, available, offline).replace(START, monday + "T00:00:00-05:00")
				.replace(END, nextMonday + "T00:00:00-05:00").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true");
		String created = send(analyst, "/measurements", body).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
		validate(id);
		return id;
	}

	@Test
	void opensOnLatestOfficialWeekWithLabels() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "10041", "39");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.resolvedPeriod.kind").value("OFFICIAL"))
				.andExpect(jsonPath("$.resolvedPeriod.timeZone").value("America/Guayaquil"))
				.andExpect(jsonPath("$.official.uptimePercent").value("99.61"))
				.andExpect(jsonPath("$.provisional").doesNotExist())
				.andExpect(jsonPath("$.trend.length()").value(1))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("LATEST_WEEK_MISSING")));
	}

	@Test
	void withoutOfficialOpensProvisionalAndWarns() throws Exception {
		validate(registered(JAC_1, "1401", "39").get("id").toString());

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.resolvedPeriod.kind").value("PROVISIONAL"))
				.andExpect(jsonPath("$.resolvedPeriod.periodType").value("ROLLING_24H"))
				.andExpect(jsonPath("$.official").doesNotExist())
				.andExpect(jsonPath("$.provisional.uptimePercent").value("97.29"))
				.andExpect(jsonPath("$.trend.length()").value(0))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("NO_OFFICIAL_WEEK")));
	}

	@Test
	void emptyGivesNullsNeverZero() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.resolvedPeriod.kind").value("NONE"))
				.andExpect(jsonPath("$.official").doesNotExist())
				.andExpect(jsonPath("$.provisional").doesNotExist());
	}

	@Test
	void noEvaluableConnectors() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("equipmentId", Feature005Fixtures.KIA_POINT.toString()))
				.andExpect(jsonPath("$.coverage.denominator").value(0))
				.andExpect(jsonPath("$.notices", Matchers.hasItem("NO_EVALUABLE_CONNECTORS")));
	}

	@Test
	void pendingCountsWithoutValue() throws Exception {
		registered(JAC_1, "1401", "39");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.coverage.byState.pendingReview").value(1))
				.andExpect(jsonPath("$.provisional").doesNotExist());
	}

	@Test
	void combinedFiltersNarrowBlocks() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "10080", "0");
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "KIA").param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.official").doesNotExist());
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "JAC").param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.official.uptimePercent").value("100.00"));
	}

	@Test
	void rangeLimitIs366Days() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("from", "2025-01-01").param("to", "2026-01-02"))
				.andExpect(status().isOk());
		mvc.perform(get(SUMMARY).cookie(manager).param("from", "2025-01-01").param("to", "2026-01-03"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_RANGE_TOO_LONG"));
	}

	@Test
	void officialRangeMustStartOnMonday() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("periodKind", "OFFICIAL").param("from", "2026-10-13")
				.param("to", "2026-10-19")).andExpect(status().isUnprocessableContent());
	}

	@Test
	void provisionalNeedsPeriodType() throws Exception {
		mvc.perform(get(SUMMARY).cookie(manager).param("periodKind", "PROVISIONAL"))
				.andExpect(status().isUnprocessableContent());
	}

	@Test
	void unattributedTotalIgnoresConnectorFilters() throws Exception {
		jdbc.execute("""
				INSERT INTO upt_import_batch (id, source, origin, file_name, checksum, original_ref, status, declared_period_type, created_at,
				 updated_at, version) VALUES ('00000000-0000-0000-0000-00000000ba01', 'ENERLINK', 'PASTE', 'x', 'c', 'r', 'COMPLETED',
				 'ROLLING_24H', now(), now(), 0)""");
		jdbc.execute("""
				INSERT INTO upt_import_row (id, batch_id, row_number, classification, issues, observations, approved, outcome,
				 version, period_end) VALUES ('00000000-0000-0000-0000-00000000ba02',
				 '00000000-0000-0000-0000-00000000ba01', 1, 'PENDING_LINK', '[]', '[]', false, 'PENDING_LINK', 0, now())""");

		mvc.perform(get(SUMMARY).cookie(manager)).andExpect(jsonPath("$.coverage.pendingLink").value(1));
		mvc.perform(get(SUMMARY).cookie(manager).param("network", "KIA"))
				.andExpect(jsonPath("$.coverage.pendingLink").value(1));
	}

	@Test
	void resultsListConnectorsEquipmentAndExclusions() throws Exception {
		official(JAC_1, "2026-10-12", "2026-10-19", "9072", "1008");

		mvc.perform(get("/api/v1/uptime/results/connectors").cookie(manager).param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].position").value(1))
				.andExpect(jsonPath("$.content[0].uptimePercent").value("90.00"))
				.andExpect(jsonPath("$.content[0].uidPuk").doesNotExist());
		mvc.perform(get("/api/v1/uptime/results/equipment").cookie(manager).param("equipmentId",
				Feature005Fixtures.JAC_STATION.toString()).param("periodKind", "OFFICIAL")
				.param("from", "2026-10-12").param("to", "2026-10-19"))
				.andExpect(jsonPath("$.content[0].connectorsIncluded").value(1))
				.andExpect(jsonPath("$.content[0].connectorsInScope").value(2));
		mvc.perform(get("/api/v1/uptime/results/exclusions").cookie(manager)).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.state == 'RETIRED')]").isNotEmpty());
	}

	@Test
	void scopeSelectorHonoursFirmScope() throws Exception {
		mvc.perform(get("/api/v1/uptime/scope/connectors").cookie(manager).param("q", "QA005"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.equipmentId == '%s')]".formatted(Feature005Fixtures.KIA_POINT)).isEmpty())
				.andExpect(jsonPath("$[?(@.equipmentId == '%s')]".formatted(Feature005Fixtures.JAC_STATION)).isNotEmpty());
	}
}
