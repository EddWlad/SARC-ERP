package com.tidsec.sarc_bakend.modules.reporting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

@SpringBootTest(
		properties = {"sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IssuedReportAccessApiTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage storage;
	private final byte[] original =
			"QA004 original exacto: información operativa".getBytes(StandardCharsets.UTF_8);

	@BeforeEach
	void clear() {
		jdbc.execute("truncate report_issued");
	}

	private UUID report(String kind, String profile) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"""
                insert into report_issued(id,kind,format,file_name,scope,checksum,size_bytes,file_ref,created_at,updated_at,version,content_profile,cut_off,criteria)
                values(?,?,'PDF','qa004.pdf','QA004 período original',?,?,'private-reference','2026-10-01','2026-10-01',0,?,
                 case when cast(? as varchar) is not null then timestamptz '2026-10-01' end,
                 case when cast(? as varchar) is not null then '{"from":"2026-10-01","to":"2026-11-01","timeZone":"America/Guayaquil"}'::jsonb end)
                """,
				id,
				kind,
				ContentFingerprint.sha256(original),
				original.length,
				profile,
				profile,
				profile);
		return id;
	}

	@Test
	void restrictedOriginalsNeverOpenStorageForReader() throws Exception {
		for (String profile : Arrays.asList(null, "WITH_OPERATIONS")) {
			UUID id = report("CONSUMPTION", profile);
			for (String role : List.of("GERENTE", "SUPERVISOR", "ASISTENTE"))
				mvc.perform(
								get("/api/v1/reports/issued/" + id + "/download")
										.with(user("qa").roles(role)))
						.andExpect(status().isForbidden())
						.andExpect(
								jsonPath("$.code").value("REPORT_OPERATIONAL_CONTENT_RESTRICTED"))
						.andExpect(jsonPath("$.action").value("ISSUE_MANAGERIAL_REPORT"));
		}
		verify(storage, never()).readDocument(anyString());
	}

	@Test
	void intermittencyOriginalsWithOperationsAreRestrictedLikeConsumptionAndOfficialOnesAreNot() throws Exception {
		when(storage.readDocument("private-reference")).thenReturn(original);
		UUID restricted = report("INTERMITTENCY", "WITH_OPERATIONS");
		UUID official = report("INTERMITTENCY", "OFFICIAL_ONLY");

		for (String role : List.of("GERENTE", "SUPERVISOR", "ASISTENTE"))
			mvc.perform(get("/api/v1/reports/issued/" + restricted + "/download").with(user("qa").roles(role)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("REPORT_OPERATIONAL_CONTENT_RESTRICTED"))
					.andExpect(jsonPath("$.action").value("ISSUE_MANAGERIAL_REPORT"));
		verify(storage, never()).readDocument(anyString());

		mvc.perform(get("/api/v1/reports/issued/" + official + "/download").with(user("qa").roles("GERENTE")))
				.andExpect(status().isOk()).andExpect(content().bytes(original));
		mvc.perform(get("/api/v1/reports/issued/" + restricted + "/download").with(user("qa").roles("ANALISTA")))
				.andExpect(status().isOk()).andExpect(content().bytes(original));
		mvc.perform(get("/api/v1/reports/issued").with(user("qa").roles("GERENTE"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + restricted + "')].canDownload").value(false))
				.andExpect(jsonPath("$.content[?(@.id=='" + restricted + "')].accessReason")
						.value("REPORT_OPERATIONAL_CONTENT_RESTRICTED"))
				.andExpect(jsonPath("$.content[?(@.id=='" + official + "')].canDownload").value(true));
	}

	@Test
	void allowedCopiesAreExactAuthenticatedBinaryWithoutProviderUrl() throws Exception {
		when(storage.readDocument("private-reference")).thenReturn(original);
		for (String kind : List.of("CONSUMPTION", "INVENTORY")) {
			UUID id = report(kind, kind.equals("CONSUMPTION") ? "OFFICIAL_ONLY" : null);
			mvc.perform(
							get("/api/v1/reports/issued/" + id + "/download")
									.with(user("qa").roles("GERENTE")))
					.andExpect(status().isOk())
					.andExpect(content().bytes(original))
					.andExpect(header().string("Content-Type", "application/pdf"))
					.andExpect(
							header().string(
											"Cache-Control",
											org.hamcrest.Matchers.containsString("no-store")))
					.andExpect(header().doesNotExist("Location"));
		}
		UUID old = report("CONSUMPTION", null);
		mvc.perform(
						get("/api/v1/reports/issued/" + old + "/download")
								.with(user("qa").roles("ANALISTA", "GERENTE")))
				.andExpect(content().bytes(original));
		assertThat(
						jdbc.queryForObject(
								"select checksum from report_issued where id=?", String.class, old))
				.isEqualTo(ContentFingerprint.sha256(original));
		verify(storage, never()).documentUrl(anyString());
	}

	@Test
	void alteredCopyIsNotDeliveredAsTheOriginal() throws Exception {
		when(storage.readDocument("private-reference"))
				.thenReturn("otro contenido".getBytes(StandardCharsets.UTF_8));
		UUID id = report("CONSUMPTION", "OFFICIAL_ONLY");
		mvc.perform(
						get("/api/v1/reports/issued/" + id + "/download")
								.with(user("qa").roles("GERENTE")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("ISSUED_REPORT_COPY_MISMATCH"));
	}

	@Test
	void metadataExplainsRestrictionWithoutInventingLegacyCutoff() throws Exception {
		report("CONSUMPTION", null);
		mvc.perform(get("/api/v1/reports/issued").with(user("qa").roles("GERENTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].canDownload").value(false))
				.andExpect(jsonPath("$.content[0].canIssueManagerial").value(true))
				.andExpect(jsonPath("$.content[0].cutOff").doesNotExist())
				.andExpect(jsonPath("$.content[0].fileRef").doesNotExist());
	}

	@Test
	void unauthenticatedNeverReadsStorage() throws Exception {
		mvc.perform(get("/api/v1/reports/issued/" + report("CONSUMPTION", null) + "/download"))
				.andExpect(status().isUnauthorized());
		verifyNoInteractions(storage);
	}
}
