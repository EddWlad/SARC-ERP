package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.tidsec.sarc_bakend.support.WebSessions;

class UptimeReportApiTest extends UptimeReportSupport {

	@Test
	void issuesPdfWithIdentityHeadersAndArchivesIt() throws Exception {
		officialWeek(JAC_1, "10041", "39");

		mvc.perform(get(REPORT).cookie(manager).param("format", "PDF")).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(header().exists("X-Report-Id")).andExpect(header().exists("X-Report-Cut-Off"))
				.andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));

		Map<String, Object> issued = jdbc.queryForMap("SELECT kind, content_profile, criteria::text AS criteria FROM report_issued");
		assertThat(issued).containsEntry("kind", "UPTIME").containsEntry("content_profile", "OFFICIAL_ONLY");
		assertThat(issued.get("criteria").toString()).contains("periodKind", "EXACT_WEEK", "2026-10-12");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'UPTIME_REPORT_GENERATED'",
				Long.class)).isEqualTo(1);
	}

	@Test
	void issuesExcelWithFilters() throws Exception {
		officialWeek(JAC_1, "10041", "39");

		mvc.perform(get(REPORT).cookie(manager).param("format", "XLSX").param("from", "2026-10-12")
				.param("to", "2026-10-19").param("periodKind", "OFFICIAL").param("network", "JAC"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", Matchers.containsString("uptime-")));

		assertThat(jdbc.queryForObject("SELECT criteria->>'network' FROM report_issued", String.class)).isEqualTo("JAC");
	}

	@Test
	void reportWithoutDataIsStillIssued() throws Exception {
		assertThat(report(manager, "PDF")).isNotEmpty();
		assertThat(jdbc.queryForObject("SELECT criteria->>'periodKind' FROM report_issued", String.class)).isEqualTo("NONE");
	}

	@Test
	void rejectsRangeOver366Days() throws Exception {
		mvc.perform(get(REPORT).cookie(manager).param("format", "PDF").param("from", "2025-01-01").param("to",
				"2026-01-03")).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UPTIME_RANGE_TOO_LONG"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM report_issued", Long.class)).isZero();
	}

	@Test
	void correctionDoesNotChangeAnIssuedReport() throws Exception {
		String id = officialWeek(JAC_1, "10041", "39");
		report(manager, "XLSX");
		String checksum = jdbc.queryForObject("SELECT checksum FROM report_issued", String.class);

		String body = correction(version(id), "10000", "80").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true");
		mvc.perform(post("/api/v1/uptime/measurements/" + id + "/corrections").cookie(analyst).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		validate(id);
		report(manager, "XLSX");

		assertThat(jdbc.queryForList("SELECT checksum FROM report_issued ORDER BY created_at", String.class)).hasSize(2)
				.first().isEqualTo(checksum);
	}

	@Test
	void requiresSession() throws Exception {
		mvc.perform(get(REPORT).param("format", "PDF")).andExpect(status().isUnauthorized());
	}
}
