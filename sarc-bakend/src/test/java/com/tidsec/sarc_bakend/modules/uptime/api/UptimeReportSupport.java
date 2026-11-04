package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;

import com.jayway.jsonpath.JsonPath;

/** Emision de reportes de uptime con mediciones oficiales y provisionales conocidas. */
abstract class UptimeReportSupport extends UptimeApiSupport {

	static final String REPORT = "/api/v1/uptime/report";

	@BeforeEach
	void prepareArchive() {
		jdbc.execute("TRUNCATE report_issued");
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenReturn("qa005/private");
	}

	String officialWeek(UUID connector, String available, String offline) throws Exception {
		String body = measurement(connector, available, offline).replace(START, "2026-10-12T00:00:00-05:00")
				.replace(END, "2026-10-19T00:00:00-05:00").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true");
		String created = send(analyst, "/measurements", body).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		String id = JsonPath.read(created, "$.id");
		validate(id);
		return id;
	}

	byte[] report(Cookie session, String format) throws Exception {
		return mvc.perform(get(REPORT).cookie(session).param("format", format)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsByteArray();
	}

	String summary(Cookie session) throws Exception {
		return mvc.perform(get("/api/v1/uptime/summary").cookie(session)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
	}
}
