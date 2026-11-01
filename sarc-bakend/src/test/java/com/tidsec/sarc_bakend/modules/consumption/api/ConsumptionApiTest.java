package com.tidsec.sarc_bakend.modules.consumption.api;

import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.Cookie;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportJobs;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Flujo completo de RF-003 contra PostgreSQL: cargar, revisar, confirmar, consultar e informar. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumptionApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";
	private static final String TYPE2 = "00000000-0000-0000-0000-000000000c01";
	private static final String CCS2 = "00000000-0000-0000-0000-000000000c02";
	private static final String WALLBOX_HEADER =
			"Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n";
	private static final String ENERLINK_HEADER =
			"ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\t"
					+ "CONECTOR\tSOC FINAL (%)\tENERGIA CARGADA (kWh)\n";

	/** Tamano del pool y de la cola de {@link ImportJobs}. */
	private static final int IMPORT_THREADS = 2;

	private static final int IMPORT_QUEUE = 10;

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired ImportJobs importJobs;
	@MockitoBean FileStorage fileStorage;
	@MockitoSpyBean EnergySessionRepository sessionRepository;

	private Cookie analyst;
	private Cookie manager;
	private String kiaPoint;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute(
				"TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role,"
					+ " auth_user, energy_import_row, energy_session_revision, energy_session,"
					+ " energy_import_batch, asset_external_identity, asset_schedule_interval,"
					+ " asset_schedule_exception, asset_connector, asset_equipment, report_issued"
					+ " CASCADE");
		when(fileStorage.storeDocument(anyString(), anyString(), any()))
				.thenReturn("sarc/imports/original");
		seedUser("analista@eos.com", Role.ANALISTA);
		seedUser("gerente@eos.com", Role.GERENTE);
		analyst = login(mvc, "analista@eos.com", PASSWORD);
		manager = login(mvc, "gerente@eos.com", PASSWORD);
		kiaPoint =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								"""
                                {"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto Quicentro","serial":"wb-1001",
                                 "assignedCode":"204518","uidPuk":"PUK-1","usageStatus":"FREE","connectorTypeIds":["%s"]}
                                """
										.formatted(TYPE2)));
	}

	// Feature 003: todas las pruebas usan PostgreSQL desechable y FileStorage mock.
	@Test
	void highReadingsFromBothSourcesAreStoredAndDecisionsAreIdempotent() throws Exception {
		conectzStationWithEnerlinkIdentity("QA", "C1");
		String wallbox = wallbox("WB-1001", "HIGH-W", "1500,000");
		String enerlink =
				ENERLINK_HEADER + enerlink("HIGH-E", "21-10-2026 08:00:00", "", "QA", "C1", "1500");
		for (String source : List.of("WALLBOX", "ENERLINK")) {
			String report = source.equals("WALLBOX") ? wallbox : enerlink;
			String external = source.equals("WALLBOX") ? "HIGH-W" : "HIGH-E";
			String batch = upload(source, "high.csv", report);
			assertThat(batch(batch))
					.containsEntry("newRows", 1)
					.containsEntry("energyReviewRows", 1);
			mvc.perform(
							get("/api/v1/energy/imports/" + batch + "/rows")
									.param("aboveThreshold", "true")
									.cookie(analyst))
					.andExpect(jsonPath("$.totalElements").value(1));
			confirm(batch);
			Map<String, Object> session = energySession(external);
			assertThat(session).containsEntry("state", "ENERGY_REVIEW");
			String id = session.get("id").toString();
			review(id, "APPROVED", "Verificada con operador", session.get("version"), analyst)
					.andExpect(status().isOk());
			long decisions =
					jdbc.queryForObject("select count(*) from energy_session_review", Long.class);
			for (int retry = 0; retry < 2; retry++) confirm(upload(source, "same.csv", report));
			assertThat(energySession(external))
					.containsEntry("energyReview", "APPROVED")
					.containsEntry("state", "VALIDATED");
			assertThat(
							jdbc.queryForObject(
									"select count(*) from energy_session_review", Long.class))
					.isEqualTo(decisions);
		}
		assertOfficial("2026-10-21", "2026-10-22", 2, 3000);
	}

	@Test
	void energyDecisionsValidatePermissionsReasonsAndVersionsIncludingSameStateRaces()
			throws Exception {
		confirm(upload("WALLBOX", "high.csv", wallbox("WB-1001", "HIGH", "1500,00")));
		Map<String, Object> session = energySession("HIGH");
		String id = session.get("id").toString();
		Object version = session.get("version");
		review(id, "APPROVED", "Texto", version, manager).andExpect(status().isForbidden());
		review(id, "APPROVED", "   ", version, analyst).andExpect(status().isBadRequest());
		review(id, "APPROVED", "x".repeat(501), version, analyst)
				.andExpect(status().isBadRequest());
		review(id, "PENDING", "Texto", version, analyst)
				.andExpect(status().isUnprocessableContent());
		review(id, "APPROVED", "x".repeat(500), version, analyst).andExpect(status().isOk());
		review(id, "REJECTED", "Antigua", version, analyst).andExpect(status().isConflict());
		Object reviewed = energySession("HIGH").get("version");
		var start = new CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			java.util.concurrent.Callable<Integer> action =
					() -> {
						start.await();
						return review(id, "APPROVED", "Otra decisión", reviewed, analyst)
								.andReturn()
								.getResponse()
								.getStatus();
					};
			var first = executor.submit(action);
			var second = executor.submit(action);
			start.countDown();
			assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 409);
		}
		assertThat(jdbc.queryForObject("select count(*) from energy_session_review", Integer.class))
				.isEqualTo(2);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'ENERGY_SESSION_REVIEWED'",
								Integer.class))
				.isEqualTo(2);
		assertThat(energySession("HIGH")).containsEntry("revision", 1);
		jdbc.update("update energy_session_review set decided_at='2026-10-21T15:00:00Z'");
		mvc.perform(get("/api/v1/energy/sessions/" + id + "/energy-reviews").cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].justification").value("Otra decisión"));
	}

	@Test
	void reviewCountsUseExclusiveStatesButEnergyFilterFindsUnlinkedAndTestSessions()
			throws Exception {
		String report =
				ENERLINK_HEADER + enerlink("P", "21-10-2026 08:00:00", "", "Unknown", "C1", "1500");
		confirm(upload("ENERLINK", "p.csv", report));
		mvc.perform(get("/api/v1/energy/sessions").param("energyReview", "PENDING").cookie(analyst))
				.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1))
				.andExpect(jsonPath("$.energyReview.sessions").value(0))
				.andExpect(jsonPath("$.official.sessions").value(0));
		conectzStationWithEnerlinkIdentity("Unknown", "C1");
		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(jsonPath("$.awaitingEnergyReview").value(1))
				.andExpect(jsonPath("$.linked").value(0));
		Map<String, Object> session = energySession("P");
		send(
						analyst,
						HttpMethod.PATCH,
						"/api/v1/energy/sessions/" + session.get("id") + "/test",
						"{\"test\":true,\"version\":" + session.get("version") + "}")
				.andExpect(status().isOk());
		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.test.sessions").value(1))
				.andExpect(jsonPath("$.energyReview.sessions").value(0));
		mvc.perform(get("/api/v1/energy/sessions").param("energyReview", "PENDING").cookie(analyst))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	private Map<String, Object> energySession(String externalId) throws Exception {
		return JsonPath.read(
				mvc.perform(
								get("/api/v1/energy/sessions")
										.param("text", externalId)
										.cookie(analyst))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsString(),
				"$.content[0]");
	}

	@Test
	void highReadingCounterIncludesInterpretableDuplicatesButExcludesInvalidReadings()
			throws Exception {
		conectzStationWithEnerlinkIdentity("QA", "C1");
		String high = enerlink("DUP", "21-10-2026 08:00:00", "", "QA", "C1", "1500");
		String id =
				upload(
						"ENERLINK",
						"counter.csv",
						ENERLINK_HEADER
								+ high
								+ high
								+ enerlink("BAD", "", "", "QA", "C1", "1500"));
		assertThat(batch(id))
				.containsEntry("energyReviewRows", 2)
				.containsEntry("newRows", 1)
				.containsEntry("duplicateRows", 1)
				.containsEntry("invalidRows", 1);
		mvc.perform(
						get("/api/v1/energy/imports/" + id + "/rows")
								.param("aboveThreshold", "true")
								.cookie(analyst))
				.andExpect(jsonPath("$.totalElements").value(2));
		mvc.perform(
						get("/api/v1/energy/imports/" + id + "/rows")
								.param("aboveThreshold", "true")
								.param("classification", "DUPLICATE")
								.cookie(analyst))
				.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(
						get("/api/v1/energy/imports/" + id + "/rows")
								.param("aboveThreshold", "false")
								.cookie(analyst))
				.andExpect(jsonPath("$.totalElements").value(1));
		confirm(id);
		assertThat(jdbc.queryForObject("select count(*) from energy_session", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void reportSizeLimitIncludesBothNewExcludedCategories() throws Exception {
		for (var state :
				List.of(
						com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState
								.ENERGY_REVIEW,
						com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState
								.ENERGY_REJECTED)) {
			doAnswer(
							invocation -> {
								SessionCriteria criteria = invocation.getArgument(0);
								return criteria.state() == state
										? new com.tidsec.sarc_bakend.modules.consumption.domain
												.repository.EnergyTotals(
												100_001, java.math.BigDecimal.ZERO, 1, 0)
										: invocation.callRealMethod();
							})
					.when(sessionRepository)
					.totals(any());
			for (String format : List.of("PDF", "XLSX")) {
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", format)
										.param("from", "2026-10-21")
										.param("to", "2026-10-22")
										.cookie(analyst))
						.andExpect(status().isUnprocessableContent())
						.andExpect(jsonPath("$.code").value("REPORT_TOO_LARGE"));
			}
		}
	}

	@Test
	void energyReviewRejectsMissingVersionCsrfUnknownAndUnobservedSessions() throws Exception {
		confirm(upload("WALLBOX", "high.csv", wallbox("WB-1001", "HIGH", "1500")));
		var high = energySession("HIGH");
		String path = "/api/v1/energy/sessions/" + high.get("id") + "/energy-review";
		send(analyst, HttpMethod.POST, path, "{\"decision\":\"APPROVED\",\"justification\":\"x\"}")
				.andExpect(status().isBadRequest());
		mvc.perform(
						request(HttpMethod.POST, path)
								.cookie(analyst)
								.contentType(MediaType.APPLICATION_JSON)
								.content(
										"{\"decision\":\"APPROVED\",\"justification\":\"x\",\"version\":0}"))
				.andExpect(status().isForbidden());
		review(java.util.UUID.randomUUID().toString(), "APPROVED", "x", 0, analyst)
				.andExpect(status().isNotFound());
		confirm(upload("WALLBOX", "normal.csv", wallbox("WB-1001", "NORMAL", "5")));
		var normal = energySession("NORMAL");
		review(normal.get("id").toString(), "APPROVED", "x", normal.get("version"), analyst)
				.andExpect(status().isUnprocessableContent());
		review(high.get("id").toString(), "APPROVED", " x ", high.get("version"), analyst)
				.andExpect(status().isOk());
		mvc.perform(get(path + "s").cookie(analyst))
				.andExpect(jsonPath("$[0].justification").value("x"));
	}

	@Test
	void
			correctionsKeepCurrentDecisionsAndReportsUseTheLatestJustificationWithoutRewritingArchives()
					throws Exception {
		conectzStationWithEnerlinkIdentity("QA", "C1");
		confirm(
				upload(
						"ENERLINK",
						"high.csv",
						ENERLINK_HEADER
								+ enerlink("HIGH", "21-10-2026 08:00:00", "", "QA", "C1", "1500")));
		var session = energySession("HIGH");
		String id = session.get("id").toString();
		for (String decision : List.of("APPROVED", "REJECTED")) {
			review(
							id,
							decision,
							"Decisión " + decision,
							energySession("HIGH").get("version"),
							analyst)
					.andExpect(status().isOk());
			String correction =
					ENERLINK_HEADER
							+ enerlink(
									"HIGH",
									"21-10-2026 08:00:00",
									decision.equals("APPROVED")
											? "21-10-2026 09:10:00"
											: "21-10-2026 09:20:00",
									"QA",
									"C1",
									"1500.0004");
			String batch = upload("ENERLINK", "correction.csv", correction);
			decide(batch, "HIGH", "\"approved\":true").andExpect(status().isOk());
			confirm(batch);
			assertThat(energySession("HIGH")).containsEntry("energyReview", decision);
		}
		assertThat(jdbc.queryForObject("select count(*) from energy_session_review", Integer.class))
				.isEqualTo(2);
		review(
						id,
						"APPROVED",
						"Verificación vigente",
						energySession("HIGH").get("version"),
						analyst)
				.andExpect(status().isOk());
		byte[] xlsx = reportBytes("XLSX");
		String archiveBefore =
				jdbc.queryForObject(
						"select checksum from report_issued order by created_at desc limit 1",
						String.class);
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			String cells = sheetText(book.getSheet("Transacciones"));
			assertThat(cells)
					.contains("Excepción de energía", "Justificación", "Verificación vigente");
			assertThat(
							valueOf(
									book.getSheet("Resumen"),
									"Aprobadas sobre umbral (sesiones, incluidas en oficiales)"))
					.isEqualTo(1);
		}
		String pdf = pdfText(reportBytes("PDF"));
		assertThat(pdf).contains("Revisión energética", "Aprobadas sobre umbral");
		for (String energy : List.of("1800", "1500", "900", "")) {
			String batch =
					upload(
							"ENERLINK",
							"changed.csv",
							ENERLINK_HEADER
									+ enerlink(
											"HIGH", "21-10-2026 08:00:00", "", "QA", "C1", energy));
			decide(batch, "HIGH", "\"approved\":true").andExpect(status().isOk());
			confirm(batch);
			assertThat(energySession("HIGH").get("energyReview"))
					.isEqualTo(energy.equals("1800") || energy.equals("1500") ? "PENDING" : null);
		}
		assertThat(jdbc.queryForList("select checksum from report_issued", String.class))
				.contains(archiveBefore);
		assertThat(jdbc.queryForObject("select count(*) from energy_session_review", Integer.class))
				.isEqualTo(3);
		assertThat(energySession("HIGH")).containsEntry("revision", 7);
	}

	@Test
	void tiedRankingIsSharedBySummaryPdfAndCompleteExcelRegardlessOfUuids() throws Exception {
		StringBuilder data = new StringBuilder(ENERLINK_HEADER);
		int[] energies = {100, 90, 80, 70, 70, 70, 60};
		for (int i = 0; i < energies.length; i++) {
			String station =
					createdId(
							send(
									analyst,
									HttpMethod.POST,
									"/api/v1/equipment",
									conectzStation("RANK-" + i)));
			verifiedIdentity(station, "QA", "C" + i);
			data.append(
					enerlink(
							"RANK-" + i,
							"21-10-2026 08:00:00",
							"",
							"QA",
							"C" + i,
							Integer.toString(energies[i])));
		}
		confirm(upload("ENERLINK", "ranking.csv", data.toString()));
		String summary =
				mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
						.andExpect(jsonPath("$.top.length()").value(6))
						.andReturn()
						.getResponse()
						.getContentAsString();
		assertThat(JsonPath.<List<Integer>>read(summary, "$.top[*].position"))
				.containsExactly(1, 2, 3, 4, 4, 4);
		List<String> names = JsonPath.read(summary, "$.top[*].equipment.name");
		String pdf = pdfText(reportBytes("PDF"));
		assertThat(pdf).contains("empate");
		for (String name : names) assertThat(pdf).contains(name);
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(reportBytes("XLSX")))) {
			String all = sheetText(book.getSheet("Por equipo"));
			for (int i = 0; i < 7; i++) assertThat(all).contains("Estacion RANK-" + i);
			List<Integer> positions = new java.util.ArrayList<>();
			for (Row row : book.getSheet("Por equipo")) {
				if (row.getCell(0) != null
						&& row.getCell(0).getCellType()
								== org.apache.poi.ss.usermodel.CellType.NUMERIC)
					positions.add((int) row.getCell(0).getNumericCellValue());
			}
			assertThat(positions).containsExactly(1, 2, 3, 4, 4, 4, 7);
		}
		// Solo datos sintéticos: simula identificadores diferentes manteniendo equipos/lecturas.
		for (int i = 0; i < 7; i++) {
			String replacement =
					createdId(
							send(
									analyst,
									HttpMethod.POST,
									"/api/v1/equipment",
									conectzStation("NEW-" + i)
											.replace("Estacion NEW-" + i, "Estacion RANK-" + i)));
			jdbc.update(
					"update energy_session set equipment_id=?::uuid where external_session_id=?",
					replacement,
					"RANK-" + i);
		}
		String after =
				mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
						.andReturn()
						.getResponse()
						.getContentAsString();
		assertThat(JsonPath.<List<String>>read(after, "$.top[*].equipment.name")).isEqualTo(names);
	}

	private byte[] reportBytes(String format) throws Exception {
		return mvc.perform(
						get("/api/v1/energy/report")
								.param("format", format)
								.param("from", "2026-10-21")
								.param("to", "2026-10-22")
								.cookie(analyst))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray();
	}

	private static String sheetText(Sheet sheet) {
		StringBuilder cells = new StringBuilder();
		sheet.forEach(row -> row.forEach(cell -> cells.append(cell).append('|')));
		return cells.toString();
	}

	private static String pdfText(byte[] bytes) throws Exception {
		try (var reader = new org.openpdf.text.pdf.PdfReader(bytes)) {
			var extractor = new org.openpdf.text.pdf.parser.PdfTextExtractor(reader);
			StringBuilder text = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++)
				text.append(extractor.getTextFromPage(page));
			return text.toString();
		}
	}

	private ResultActions review(
			String id, String decision, String reason, Object version, Cookie user)
			throws Exception {
		return send(
				user,
				HttpMethod.POST,
				"/api/v1/energy/sessions/" + id + "/energy-review",
				"{\"decision\":\""
						+ decision
						+ "\",\"justification\":\""
						+ reason
						+ "\",\"version\":"
						+ version
						+ "}");
	}

	@Test
	void mixedBatchProtectsFeature002AndKeepsEnergyDecisionsWhenReimported() throws Exception {
		String first = conectzStationWithEnerlinkIdentity("QA", "C1");
		confirm(
				upload(
						"ENERLINK",
						"before.csv",
						ENERLINK_HEADER
								+ enerlink("L", "21-10-2026 08:00:00", "", "Later", "C1", "20")
								+ enerlink("C", "21-10-2026 08:00:00", "", "QA", "C1", "10")));
		verifiedIdentity(first, "Later", "C1");
		verifiedIdentity(first, "Ambiguous", "C1");
		String second =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("OTHER")));
		insertVerifiedIdentity(second, "AMBIGUOUS", "c1");
		String report =
				ENERLINK_HEADER
						+ enerlink("N", "21-10-2026 08:00:00", "", "QA", "C1", "2")
						+ enerlink("Z", "21-10-2026 08:00:00", "", "QA", "C1", "0")
						+ enerlink("M", "21-10-2026 08:00:00", "", "QA", "C1", "")
						+ enerlink("H", "21-10-2026 08:00:00", "", "QA", "C1", "1500")
						+ enerlink("U", "21-10-2026 08:00:00", "", "Ambiguous", "C1", "5")
						+ enerlink("L", "21-10-2026 08:00:00", "", "Later", "C1", "20")
						+ enerlink("N", "21-10-2026 08:00:00", "", "QA", "C1", "2")
						+ enerlink("C", "21-10-2026 08:00:00", "", "QA", "C1", "12")
						+ enerlink("X", "21-10-2026 08:00:00", "", "QA", "C1", "-1");
		String id = upload("ENERLINK", "mixed.csv", report);
		assertThat(batch(id))
				.containsEntry("totalRows", 9)
				.containsEntry("newRows", 4)
				.containsEntry("pendingLinkRows", 1)
				.containsEntry("linkableRows", 1)
				.containsEntry("duplicateRows", 1)
				.containsEntry("modifiedRows", 1)
				.containsEntry("invalidRows", 1)
				.containsEntry("energyReviewRows", 1);
		decide(id, "C", "\"approved\":true").andExpect(status().isOk());
		confirm(id);
		assertOfficial("2026-10-21", "2026-10-22", 5, 34);
		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.official.sessionsWithoutEnergy").value(1))
				.andExpect(jsonPath("$.energyReview.sessions").value(1))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1));
		for (int i = 0; i < 2; i++) confirm(upload("ENERLINK", "same.csv", report));
		assertThat(jdbc.queryForObject("select count(*) from energy_session", Integer.class))
				.isEqualTo(7);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from energy_session_revision", Integer.class))
				.isEqualTo(1);
		Map<String, Object> high = energySession("H");
		review(high.get("id").toString(), "APPROVED", "Verificado", high.get("version"), analyst)
				.andExpect(status().isOk());
		for (int i = 0; i < 2; i++) confirm(upload("ENERLINK", "same.csv", report));
		assertOfficial("2026-10-21", "2026-10-22", 6, 1534);
		assertThat(jdbc.queryForObject("select count(*) from energy_session_review", Integer.class))
				.isEqualTo(1);
		byte[] xlsx =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "XLSX")
										.param("from", "2026-10-21")
										.param("to", "2026-10-22")
										.cookie(analyst))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(valueOf(book.getSheet("Resumen"), "Sesiones")).isEqualTo(6);
			assertThat(valueOf(book.getSheet("Resumen"), "Energía conocida (kWh)")).isEqualTo(1534);
			assertThat(book.getSheet("Transacciones").getRow(4).getLastCellNum())
					.isGreaterThan((short) 14);
		}
		mvc.perform(
						get("/api/v1/energy/report")
								.param("format", "PDF")
								.param("from", "2026-10-21")
								.param("to", "2026-10-22")
								.cookie(manager))
				.andExpect(status().isOk());
		high = energySession("H");
		review(
						high.get("id").toString(),
						"REJECTED",
						"Lectura rechazada",
						high.get("version"),
						analyst)
				.andExpect(status().isOk());
		confirm(upload("ENERLINK", "same.csv", report));
		assertThat(energySession("H")).containsEntry("energyReview", "REJECTED");
		assertOfficial("2026-10-21", "2026-10-22", 5, 34);
	}

	@Test
	void sourceAndEnergyApprovalsCanBeAppliedInEitherOrderThroughTheApi() throws Exception {
		conectzStationWithEnerlinkIdentity("QA", "C1");
		for (boolean sourceFirst : new boolean[] {true, false}) {
			String external = sourceFirst ? "FIRST" : "LAST";
			String serial = "SERIAL-" + external;
			confirm(upload("WALLBOX", "nonpreferred.csv", wallbox(serial, external, "1500,00")));
			String station =
					jdbc.queryForObject(
							"select id::text from asset_equipment where serial = 'CZ-4'",
							String.class);
			String identity =
					createdId(
							send(
									analyst,
									HttpMethod.POST,
									"/api/v1/equipment/" + station + "/external-identities",
									"{\"platform\":\"WALLBOX\",\"externalId\":\""
											+ serial
											+ "\"}"));
			send(
							analyst,
							HttpMethod.POST,
							"/api/v1/external-identities/" + identity + "/verify",
							"{\"version\":0}")
					.andExpect(status().isOk());
			send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
					.andExpect(status().isOk());
			Map<String, Object> current = energySession(external);
			String sid = current.get("id").toString();
			assertThat(current).containsEntry("canApproveSource", true);
			if (sourceFirst)
				send(
								analyst,
								HttpMethod.POST,
								"/api/v1/energy/sessions/" + sid + "/source-approval",
								"{\"version\":" + current.get("version") + "}")
						.andExpect(status().isOk());
			current = energySession(external);
			review(sid, "APPROVED", "Confirmada", current.get("version"), analyst)
					.andExpect(status().isOk());
			if (!sourceFirst)
				send(
								analyst,
								HttpMethod.POST,
								"/api/v1/energy/sessions/" + sid + "/source-approval",
								"{\"version\":" + energySession(external).get("version") + "}")
						.andExpect(status().isOk());
			assertThat(energySession(external)).containsEntry("state", "VALIDATED");
		}
	}

	// ---- idempotencia y formatos (CA-003-01, CA-003-02)

	@Test
	void reimportingTheSameFileAddsNeitherSessionsNorEnergy() throws Exception {
		String report =
				WALLBOX_HEADER
						+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t35,41\n"
						+ "WB-1001\tPlaza\tS-2\t2026-10-21 10:00:00\t2026-10-21 10:05:00\t0,00\n";

		String first = upload("WALLBOX", "SessionsReport.csv", report);
		assertThat(batch(first)).containsEntry("newRows", 2);
		confirm(first);
		assertOfficial("2026-10-21", "2026-10-22", 2, 35.41);

		String second = upload("WALLBOX", "SessionsReport.csv", report);
		assertThat(batch(second)).containsEntry("identicalRows", 2).containsEntry("newRows", 0);
		assertThat(confirm(second)).containsEntry("appliedRows", 0);
		assertOfficial("2026-10-21", "2026-10-22", 2, 35.41);
		// El trabajo en segundo plano actua en nombre de quien cargo el lote.
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'ENERGY_IMPORT_COMPLETED' and actor_id is not null",
								Integer.class))
				.isEqualTo(2);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from energy_session where created_by is null",
								Integer.class))
				.isZero();
	}

	// ---- dia de conteo y red oficial (CA-003-03, CA-003-04)

	@Test
	void sessionIsCountedOnItsStartDayAndItsNetworkComesFromTheInventory() throws Exception {
		String station = conectzStationWithEnerlinkIdentity("Conectz", "ESTACION 4");

		String id =
				upload(
						"ENERLINK",
						"Transacciones_KIA.csv",
						ENERLINK_HEADER
								+ "27151\t20-10-2026 23:30:00\t21-10-2026 00:10:00\tConectz"
								+ "\tESTACION 4\tA - CCS1\t80\t22.42\n");
		confirm(id);

		mvc.perform(get("/api/v1/energy/sessions").cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].startDate").value("2026-10-20"))
				.andExpect(jsonPath("$.content[0].equipment.id").value(station))
				.andExpect(jsonPath("$.content[0].equipment.network").value("CONECTZ"))
				.andExpect(jsonPath("$.content[0].state").value("VALIDATED"));
		mvc.perform(summary("2026-10-20", "2026-10-21").param("network", "KIA").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(0));
		assertOfficial("2026-10-20", "2026-10-21", 1, 22.42);
	}

	@Test
	void sessionsMarkedAsTestInThePreviewAreKeptButExcluded() throws Exception {
		conectzStationWithEnerlinkIdentity("Conectz", "ESTACION 4");
		String id =
				upload(
						"ENERLINK",
						"transacciones.csv",
						ENERLINK_HEADER
								+ "500\t21-10-2026 08:00:00\t21-10-2026 08:30:00\tConectz\tESTACION"
								+ " 4\tA - CCS1\t80\t10\n"
								+ "501\t21-10-2026 09:00:00\t21-10-2026 09:30:00\tConectz\tESTACION"
								+ " 4\tA - CCS1\t80\t5\n");
		String testRow = rowId(id, "501");

		send(
						analyst,
						HttpMethod.PUT,
						"/api/v1/energy/imports/" + id + "/rows/decision",
						"{\"rowIds\":[\"" + testRow + "\"],\"test\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].test").value(true));
		confirm(id);

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.official.sessions").value(1))
				.andExpect(jsonPath("$.official.energyKwh").value(10.0))
				.andExpect(jsonPath("$.test.sessions").value(1))
				.andExpect(jsonPath("$.test.energyKwh").value(5.0));
	}

	// ---- conciliacion de equipos desconocidos (RF-003.7)

	@Test
	void unknownChargerStaysPendingUntilItsIdentityIsVerified() throws Exception {
		String station =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-9")));
		String id =
				upload(
						"ENERLINK",
						"transacciones.csv",
						ENERLINK_HEADER
								+ "900\t21-10-2026 08:00:00\t21-10-2026 08:30:00\tConectz"
								+ " Aeropuerto\tAEROPUERTO 1\t1 - CCS2\t80\t7.5\n");
		assertThat(batch(id)).containsEntry("pendingLinkRows", 1);
		confirm(id);

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.official.sessions").value(0))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1));

		verifiedIdentity(station, "Conectz Aeropuerto", "AEROPUERTO 1");
		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.linked").value(1))
				.andExpect(jsonPath("$.stillPending").value(0));
		assertOfficial("2026-10-21", "2026-10-22", 1, 7.5);
	}

	// ---- correcciones con aprobacion (RF-003.6)

	@Test
	void changedSessionIsOnlyCorrectedWithApprovalAndKeepsTheOldVersion() throws Exception {
		confirm(
				upload(
						"WALLBOX",
						"r1.csv",
						WALLBOX_HEADER
								+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00"
								+ "\t10,00\n"));
		String changed =
				WALLBOX_HEADER
						+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:10:00\t12,50\n";

		String unapproved = upload("WALLBOX", "r2.csv", changed);
		assertThat(batch(unapproved)).containsEntry("modifiedRows", 1);
		assertThat(confirm(unapproved)).containsEntry("appliedRows", 0);
		assertOfficial("2026-10-21", "2026-10-22", 1, 10.0);

		String approved = upload("WALLBOX", "r3.csv", changed);
		send(
						analyst,
						HttpMethod.PUT,
						"/api/v1/energy/imports/" + approved + "/rows/decision",
						"{\"rowIds\":[\"" + rowId(approved, "S-1") + "\"],\"approved\":true}")
				.andExpect(status().isOk());
		assertThat(confirm(approved)).containsEntry("appliedRows", 1);
		assertOfficial("2026-10-21", "2026-10-22", 1, 12.5);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from energy_session_revision", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select revision from energy_session", Integer.class))
				.isEqualTo(2);
	}

	// ---- errores de interpretacion

	@Test
	void invalidRowsAreReportedAndOnlyValidOnesAreImported() throws Exception {
		String id =
				upload(
						"WALLBOX",
						"r.csv",
						WALLBOX_HEADER
								+ "WB-1001\tPlaza\tS-1\t31/02/2026 08:00:00\t2026-10-21 09:00:00"
								+ "\t1,5\n"
								+ "WB-1001\tPlaza\tS-2\t2026-10-21 08:00:00\t2026-10-21 09:00:00"
								+ "\t1,5\n");

		assertThat(batch(id)).containsEntry("invalidRows", 1).containsEntry("newRows", 1);
		mvc.perform(
						get("/api/v1/energy/imports/" + id + "/rows")
								.param("classification", "INVALID")
								.cookie(analyst))
				.andExpect(jsonPath("$.content[0].rowNumber").value(2))
				.andExpect(jsonPath("$.content[0].issues[0].code").value("INVALID_DATE"));
		assertThat(confirm(id)).containsEntry("appliedRows", 1);
	}

	@Test
	void aFileFromAnotherPlatformFailsWithAReadableReason() throws Exception {
		String id =
				JsonPath.read(
						mvc.perform(
										multipart("/api/v1/energy/imports")
												.file(
														new MockMultipartFile(
																"file",
																"x.csv",
																"text/csv",
																(WALLBOX_HEADER
																				+ "WB-1001\tPlaza"
																				+ "\tS-1"
																				+ "\t2026-10-21"
																				+ " 08:00:00"
																				+ "\t2026-10-21"
																				+ " 09:00:00\t1\n")
																		.getBytes(
																				StandardCharsets
																						.UTF_8)))
												.param("source", "ENERLINK")
												.with(xsrf())
												.cookie(analyst))
								.andExpect(status().isAccepted())
								.andReturn()
								.getResponse()
								.getContentAsString(),
						"$.id");

		Map<String, Object> failed = awaitStatus(id, "FAILED");
		assertThat(failed).containsEntry("failureCode", "IMPORT_COLUMNS_MISSING");
	}

	// ---- reportes y permisos (CA-003-05, PRD 4)

	@Test
	void reportTotalsMatchTheDashboard() throws Exception {
		confirm(
				upload(
						"WALLBOX",
						"r.csv",
						WALLBOX_HEADER
								+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00"
								+ "\t35,41\n"
								+ "WB-1001\tPlaza\tS-2\t2026-10-22 08:00:00\t2026-10-22 09:00:00"
								+ "\t4,59\n"));

		mvc.perform(summary("2026-10-01", "2026-11-01").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(2))
				.andExpect(jsonPath("$.official.energyKwh").value(40.0))
				.andExpect(jsonPath("$.official.equipment").value(1))
				.andExpect(jsonPath("$.top[0].equipment.id").value(kiaPoint))
				.andExpect(jsonPath("$.daily.length()").value(31))
				.andExpect(jsonPath("$.byNetwork.length()").value(3))
				.andExpect(jsonPath("$.byNetwork[?(@.network == 'KIA')].energyKwh").value(40.0))
				.andExpect(jsonPath("$.byNetwork[?(@.network == 'JAC')].energyKwh").value(0))
				.andExpect(jsonPath("$.bySource[?(@.source == 'WALLBOX')].sessions").value(2))
				.andExpect(
						jsonPath("$.byType[?(@.type == 'CHARGING_POINT')].energyKwh").value(40.0));

		byte[] xlsx =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "XLSX")
										.param("from", "2026-10-01")
										.param("to", "2026-11-01")
										.cookie(manager))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet summary = workbook.getSheet("Resumen");
			assertThat(valueOf(summary, "Energía (kWh)")).isEqualTo(40.0);
			assertThat(valueOf(summary, "Sesiones")).isEqualTo(2.0);
			assertThat(workbook.getSheet("Transacciones").getLastRowNum()).isEqualTo(6);
		}
		byte[] pdf =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "PDF")
										.param("from", "2026-10-01")
										.param("to", "2026-11-01")
										.cookie(manager))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	@Test
	void everyoneConsultsButOnlyEditorsImport() throws Exception {
		mvc.perform(summary("2026-10-01", "2026-11-01").cookie(manager)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/energy/sessions").cookie(manager)).andExpect(status().isOk());
		mvc.perform(
						multipart("/api/v1/energy/imports")
								.file(
										new MockMultipartFile(
												"file",
												"x.csv",
												"text/csv",
												WALLBOX_HEADER.getBytes(StandardCharsets.UTF_8)))
								.param("source", "WALLBOX")
								.with(xsrf())
								.cookie(manager))
				.andExpect(status().isForbidden());
		send(manager, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(status().isForbidden());
		mvc.perform(summary("2026-10-05", "2026-10-01").cookie(manager))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
	}

	// ---- regresiones de la revision QA del 26-oct-2026

	@Test
	void aSessionThatChangesChargerIsOnlyMovedByAnExplicitReassignment() throws Exception {
		// QA-01: aprobar la correccion no basta; mover la sesion a otro equipo es una decision
		// aparte.
		String secondPoint =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								"""
                                {"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto Norte","serial":"WB-2002",
                                 "assignedCode":"204519","uidPuk":"PUK-2","usageStatus":"FREE","connectorTypeIds":["%s"]}
                                """
										.formatted(TYPE2)));
		confirm(upload("WALLBOX", "r1.csv", wallbox("WB-1001", "S-1", "10,00")));

		String ignored = upload("WALLBOX", "r2.csv", wallbox("WB-2002", "S-1", "12,00"));
		assertThat(batch(ignored))
				.containsEntry("equipmentChangedRows", 1)
				.containsEntry("modifiedRows", 0);
		decide(ignored, "S-1", "\"approved\":true")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ROW_APPROVAL_NOT_REQUIRED"));
		assertThat(confirm(ignored)).containsEntry("appliedRows", 0);
		assertThat(jdbc.queryForObject("select source_serial from energy_session", String.class))
				.isEqualTo("WB-1001");

		String reassigned = upload("WALLBOX", "r3.csv", wallbox("WB-2002", "S-1", "12,00"));
		decide(reassigned, "S-1", "\"reassign\":true")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reassigned").value(true));
		assertThat(confirm(reassigned)).containsEntry("appliedRows", 1);
		assertThat(
						jdbc.queryForMap(
								"select source_serial, equipment_id::text as equipment, revision"
									+ " from energy_session"))
				.containsEntry("source_serial", "WB-2002")
				.containsEntry("equipment", secondPoint)
				.containsEntry("revision", 2);
		assertThat(
						jdbc.queryForObject(
								"select equipment_id::text from energy_session_revision",
								String.class))
				.isEqualTo(kiaPoint);
		assertThat(
						jdbc.queryForObject(
								"select outcome from energy_import_row where batch_id = ?::uuid",
								String.class,
								reassigned))
				.isEqualTo("REASSIGNED");
	}

	@Test
	void anApprovalGivenAgainstAnOlderRevisionDoesNotOverwriteANewerCorrection() throws Exception {
		// QA-02: dos lotes revisados sobre la revision 1; el segundo no puede pisar lo que aplico
		// el primero.
		confirm(upload("WALLBOX", "r1.csv", wallbox("WB-1001", "S-1", "10,00")));
		String first = upload("WALLBOX", "a.csv", wallbox("WB-1001", "S-1", "12,00"));
		String second = upload("WALLBOX", "b.csv", wallbox("WB-1001", "S-1", "15,00"));
		decide(first, "S-1", "\"approved\":true").andExpect(status().isOk());
		decide(second, "S-1", "\"approved\":true").andExpect(status().isOk());

		assertThat(confirm(first)).containsEntry("appliedRows", 1);
		assertThat(confirm(second)).containsEntry("appliedRows", 0);

		assertOfficial("2026-10-21", "2026-10-22", 1, 12.0);
		assertThat(
						jdbc.queryForMap(
								"select outcome, outcome_detail from energy_import_row where"
									+ " batch_id = ?::uuid",
								second))
				.containsEntry("outcome", "SKIPPED")
				.extractingByKey("outcome_detail")
				.asString()
				.contains("vista previa");
	}

	@Test
	void aNonPreferredSourceLinkedLaterWaitsForApproval() throws Exception {
		// QA-03: Wallbox de una estacion Conectz queda pendiente; al vincularla tampoco suma sin
		// aprobacion.
		confirm(upload("WALLBOX", "pending.csv", wallbox("WB-LATE", "S-1", "10,00")));
		createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", conectzStation("WB-LATE")));

		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.linked").value(0))
				.andExpect(jsonPath("$.awaitingApproval").value(1))
				.andExpect(jsonPath("$.stillPending").value(0));
		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.official.sessions").value(0))
				.andExpect(jsonPath("$.awaitingApproval.sessions").value(1))
				.andExpect(jsonPath("$.awaitingApproval.energyKwh").value(10.0));

		String session =
				mvc.perform(get("/api/v1/energy/sessions").cookie(analyst))
						.andExpect(jsonPath("$.content[0].state").value("AWAITING_APPROVAL"))
						.andReturn()
						.getResponse()
						.getContentAsString();
		String id = JsonPath.read(session, "$.content[0].id");
		int version = JsonPath.read(session, "$.content[0].version");
		send(
						manager,
						HttpMethod.POST,
						"/api/v1/energy/sessions/" + id + "/source-approval",
						"{\"version\":" + version + "}")
				.andExpect(status().isForbidden());
		send(
						analyst,
						HttpMethod.POST,
						"/api/v1/energy/sessions/" + id + "/source-approval",
						"{\"version\":" + version + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("VALIDATED"));
		assertOfficial("2026-10-21", "2026-10-22", 1, 10.0);
	}

	@Test
	void anOverlongValueInvalidatesOnlyItsRow() throws Exception {
		// QA-07: la serie va en VARCHAR(80); antes de esta validacion abortaba todo el lote al
		// guardar.
		String id =
				upload(
						"WALLBOX",
						"long.csv",
						wallbox("X".repeat(81), "S-1", "10,00")
								+ "WB-1001\tPlaza\t"
								+ "Y".repeat(101)
								+ "\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t5,00\n"
								+ "WB-1001\tPlaza\tVALID\t2026-10-21 08:00:00\t2026-10-21 09:00:00"
								+ "\t5,00\n");

		assertThat(batch(id)).containsEntry("invalidRows", 2).containsEntry("newRows", 1);
		assertThat(confirm(id)).containsEntry("appliedRows", 1);
	}

	@Test
	void reportSummaryAndDetailComeFromTheSameSnapshot() throws Exception {
		// QA-05: una correccion confirmada mientras se genera el reporte no puede descuadrarlo.
		confirm(upload("WALLBOX", "r.csv", wallbox("WB-1001", "S-1", "10,00")));
		AtomicBoolean injected = new AtomicBoolean();
		doAnswer(
						invocation -> {
							if (injected.compareAndSet(false, true)) {
								Thread writer =
										new Thread(
												() ->
														jdbc.update(
																"update energy_session set"
																	+ " energy_kwh = 20, revision ="
																	+ " revision + 1, version ="
																	+ " version + 1"));
								writer.start();
								writer.join();
							}
							return invocation.callRealMethod();
						})
				.when(sessionRepository)
				.findAll(any(SessionCriteria.class));

		byte[] xlsx =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "XLSX")
										.param("from", "2026-10-21")
										.param("to", "2026-10-22")
										.cookie(manager))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();

		assertThat(injected).isTrue();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(valueOf(workbook.getSheet("Resumen"), "Energía (kWh)")).isEqualTo(10.0);
			assertThat(
							workbook.getSheet("Transacciones")
									.getRow(5)
									.getCell(5)
									.getNumericCellValue())
					.isEqualTo(10.0);
		}
	}

	@Test
	void everyIssuedReportIsArchivedWithItsChecksumAndCanBeDownloadedAgain() throws Exception {
		// Lectura-01 (RF-RPT-08): se conserva la copia exacta que se entrego.
		when(fileStorage.storeDocument(anyString(), anyString(), any()))
				.thenReturn("sarc/reports/copia");

		byte[] pdf =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "PDF")
										.param("from", "2026-10-01")
										.param("to", "2026-11-01")
										.cookie(manager))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();

		when(fileStorage.readDocument("sarc/reports/copia")).thenReturn(pdf);
		String issued =
				mvc.perform(
								get("/api/v1/reports/issued")
										.param("kind", "CONSUMPTION")
										.cookie(manager))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.content.length()").value(1))
						.andExpect(jsonPath("$.content[0].format").value("PDF"))
						.andExpect(jsonPath("$.content[0].archived").value(true))
						.andExpect(jsonPath("$.content[0].sizeBytes").value(pdf.length))
						.andExpect(
								jsonPath("$.content[0].checksum")
										.value(
												HexFormat.of()
														.formatHex(
																MessageDigest.getInstance("SHA-256")
																		.digest(pdf))))
						.andReturn()
						.getResponse()
						.getContentAsString();
		mvc.perform(
						get("/api/v1/reports/issued/"
										+ JsonPath.read(issued, "$.content[0].id")
										+ "/download")
								.cookie(manager))
				.andExpect(status().isOk())
				.andExpect(
						org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
								.bytes(pdf));
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'CONSUMPTION_REPORT_GENERATED' and entity_type ="
									+ " 'ISSUED_REPORT'",
								Integer.class))
				.isEqualTo(1);
	}

	@Test
	void aReportIsNotDeliveredWhenItsCopyCannotBeStored() throws Exception {
		// Un documento emitido debe poder recuperarse; solo un entorno sin almacenamiento emite sin
		// copia.
		doThrow(new ServiceUnavailableException("STORAGE_UNAVAILABLE", "Almacenamiento caido"))
				.when(fileStorage)
				.storeDocument(anyString(), anyString(), any());
		mvc.perform(
						get("/api/v1/energy/report")
								.param("format", "PDF")
								.param("from", "2026-10-01")
								.param("to", "2026-11-01")
								.cookie(manager))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("REPORT_ARCHIVE_UNAVAILABLE"));
		assertThat(jdbc.queryForObject("select count(*) from report_issued", Integer.class))
				.isZero();

		doThrow(new ServiceUnavailableException(FileStorage.NOT_CONFIGURED, "Sin almacenamiento"))
				.when(fileStorage)
				.storeDocument(anyString(), anyString(), any());
		mvc.perform(
						get("/api/v1/energy/report")
								.param("format", "PDF")
								.param("from", "2026-10-01")
								.param("to", "2026-11-01")
								.cookie(manager))
				.andExpect(status().isOk());
		assertThat(
						jdbc.queryForObject(
								"select count(*) from report_issued where file_ref is null",
								Integer.class))
				.isEqualTo(1);
	}

	@Test
	void aBatchRejectedByAFullQueueIsLeftFailedInsteadOfParsingForever() throws Exception {
		// QA-06: sin trabajador que lo tome, el lote se marca FAILED en una transaccion propia.
		CountDownLatch busy = new CountDownLatch(IMPORT_THREADS);
		CountDownLatch release = new CountDownLatch(1);
		Runnable blocking =
				() -> {
					busy.countDown();
					try {
						release.await(20, TimeUnit.SECONDS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				};
		try {
			for (int i = 0; i < IMPORT_THREADS; i++) {
				importJobs.submitAfterCommit(blocking, () -> fail("Rechazo inesperado"));
			}
			assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();
			for (int i = 0; i < IMPORT_QUEUE; i++) {
				importJobs.submitAfterCommit(() -> {}, () -> fail("Rechazo inesperado"));
			}
			mvc.perform(
					multipart("/api/v1/energy/imports")
							.file(
									new MockMultipartFile(
											"file",
											"cola.csv",
											"text/csv",
											wallbox("WB-1001", "S-1", "1,00")
													.getBytes(StandardCharsets.UTF_8)))
							.param("source", "WALLBOX")
							.with(xsrf())
							.cookie(analyst));

			assertThat(jdbc.queryForObject("select status from energy_import_batch", String.class))
					.isEqualTo("FAILED");
		} finally {
			release.countDown();
		}
	}

	@Test
	void freeEquipmentWithoutCodeKeepsConsumptionHistoryAndArchivedReportsAcrossUsageChanges()
			throws Exception {
		String equipment =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								"""
                                {"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Free sin codigo",
                                 "serial":"FREE-HISTORY","uidPuk":"U-FREE","usageStatus":"FREE","connectorTypeIds":["%s"]}
                                """
										.formatted(TYPE2)));
		confirm(upload("WALLBOX", "original.csv", wallbox("FREE-HISTORY", "FREE-S1", "10.00")));
		String correction =
				upload("WALLBOX", "correction.csv", wallbox("FREE-HISTORY", "FREE-S1", "12.50"));
		send(
						analyst,
						HttpMethod.PUT,
						"/api/v1/energy/imports/" + correction + "/rows/decision",
						"{\"rowIds\":[\"" + rowId(correction, "FREE-S1") + "\"],\"approved\":true}")
				.andExpect(status().isOk());
		confirm(correction);
		// A verified external Wallbox identity can resolve a different source serial to the same
		// UUID.
		String identity =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment/" + equipment + "/external-identities",
								"{\"platform\":\"WALLBOX\",\"externalId\":\"EXTERNAL-FREE\"}"));
		send(
						analyst,
						HttpMethod.POST,
						"/api/v1/external-identities/" + identity + "/verify",
						"{\"version\":0}")
				.andExpect(status().isOk());
		confirm(upload("WALLBOX", "external.csv", wallbox("EXTERNAL-FREE", "FREE-S2", "7.50")));
		assertOfficial("2026-10-21", "2026-10-22", 2, 20.0);
		mvc.perform(
						summary("2026-10-21", "2026-10-22")
								.param("equipmentId", equipment)
								.cookie(manager))
				.andExpect(jsonPath("$.official.energyKwh").value(20.0))
				.andExpect(jsonPath("$.top[0].equipment.id").value(equipment))
				.andExpect(jsonPath("$.top[0].equipment.assignedCode").isEmpty());
		mvc.perform(get("/api/v1/energy/sessions").param("equipmentId", equipment).cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].equipment.assignedCode").isEmpty());
		String sessions =
				jdbc.queryForObject(
						"select jsonb_agg(to_jsonb(s) order by id)::text from energy_session s",
						String.class);
		String revisions =
				jdbc.queryForObject(
						"select jsonb_agg(to_jsonb(s) order by id)::text from"
							+ " energy_session_revision s",
						String.class);
		var copies = new java.util.HashMap<String, byte[]>();
		when(fileStorage.storeDocument(anyString(), anyString(), any()))
				.thenAnswer(
						invocation -> {
							String ref = "sandbox/" + java.util.UUID.randomUUID();
							copies.put(ref, ((byte[]) invocation.getArgument(2)).clone());
							return ref;
						});
		for (String format : List.of("PDF", "XLSX")) {
			byte[] bytes =
					mvc.perform(
									get("/api/v1/energy/report")
											.param("format", format)
											.param("from", "2026-10-21")
											.param("to", "2026-10-22")
											.param("equipmentId", equipment)
											.cookie(manager))
							.andExpect(status().isOk())
							.andReturn()
							.getResponse()
							.getContentAsByteArray();
			assertThat(copies.values()).anySatisfy(stored -> assertThat(stored).isEqualTo(bytes));
			if (format.equals("XLSX")) {
				try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
					assertThat(
									valueOf(
											workbook.getSheet("Resumen"),
											"Energ" + (char) 237 + "a (kWh)"))
							.isEqualTo(20.0);
					var cells = new StringBuilder();
					workbook.forEach(
							sheet ->
									sheet.forEach(
											row ->
													row.forEach(
															cell ->
																	cells.append(cell.toString())
																			.append("|"))));
					assertThat(cells.toString()).doesNotContain("null", "undefined");
				}
			} else
				assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		}
		String issued =
				jdbc.queryForObject(
						"select jsonb_agg(to_jsonb(r) order by id)::text from report_issued r",
						String.class);
		var originalCopies = new java.util.HashMap<String, byte[]>();
		copies.forEach((key, value) -> originalCopies.put(key, value.clone()));
		for (String usage : List.of("BLOCKED", "FREE")) {
			int version =
					jdbc.queryForObject(
							"select version from asset_equipment where id=?::uuid",
							Integer.class,
							equipment);
			send(
							analyst,
							HttpMethod.PUT,
							"/api/v1/equipment/" + equipment,
							"""
                            {"version":%d,"brand":"WALLBOX","name":"Free sin codigo","serial":"FREE-HISTORY",
                            "assignedCode":"654321","uidPuk":"U-FREE","usageStatus":"%s","connectorTypeIds":["%s"]}
                            """
									.formatted(version, usage, TYPE2))
					.andExpect(status().isOk());
		}
		assertOfficial("2026-10-21", "2026-10-22", 2, 20.0);
		assertThat(
						jdbc.queryForObject(
								"select jsonb_agg(to_jsonb(s) order by id)::text from"
									+ " energy_session s",
								String.class))
				.isEqualTo(sessions);
		assertThat(
						jdbc.queryForObject(
								"select jsonb_agg(to_jsonb(s) order by id)::text from"
									+ " energy_session_revision s",
								String.class))
				.isEqualTo(revisions);
		assertThat(
						jdbc.queryForObject(
								"select jsonb_agg(to_jsonb(r) order by id)::text from report_issued"
									+ " r",
								String.class))
				.isEqualTo(issued);
		originalCopies.forEach((key, value) -> assertThat(copies.get(key)).isEqualTo(value));
		for (var entry : copies.entrySet()) {
			String checksum =
					HexFormat.of()
							.formatHex(
									MessageDigest.getInstance("SHA-256").digest(entry.getValue()));
			assertThat(
							jdbc.queryForObject(
									"select checksum from report_issued where file_ref=?",
									String.class,
									entry.getKey()))
					.isEqualTo(checksum);
		}
	}

	// ---- vinculacion Enerlink (feature 002, US2)

	@Test
	void fiveSessionsWithTheSamePairLinkToOneEquipmentEvenWithDifferentWriting() throws Exception {
		String station = conectzStationWithEnerlinkIdentity("JAC PUMA TUMBACO", "JAC PUMA TUMBACO");
		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"JAC PUMA TUMBACO",
										"JAC PUMA TUMBACO",
										"1")
								+ enerlink(
										"2",
										"21-10-2026 09:00:00",
										"21-10-2026 09:30:00",
										"jac puma tumbaco",
										"JAC  PUMA TUMBACO",
										"1")
								+ enerlink(
										"3",
										"21-10-2026 10:00:00",
										"21-10-2026 10:30:00",
										"JÁC PUMA TUMBACO",
										"JAC PUMA TÚMBACO",
										"1")
								+ enerlink(
										"4",
										"21-10-2026 11:00:00",
										"21-10-2026 11:30:00",
										" JAC PUMA TUMBACO ",
										"JAC PUMA TUMBACO",
										"1")
								+ enerlink(
										"5",
										"21-10-2026 12:00:00",
										"21-10-2026 12:30:00",
										"JAC PUMA TUMBACO",
										"jac puma tumbaco",
										"1"));

		assertThat(batch(id)).containsEntry("newRows", 5).containsEntry("pendingLinkRows", 0);
		confirm(id);

		assertOfficial("2026-10-21", "2026-10-22", 5, 5.0);
		assertThat(
						jdbc.queryForObject(
								"select count(distinct equipment_id) from energy_session where"
									+ " equipment_id = ?::uuid",
								Integer.class,
								station))
				.isEqualTo(1);
	}

	@Test
	void everyUnresolvedSessionStaysPendingWithItsSpecificReason() throws Exception {
		String first =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-1")));
		String second =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-2")));
		send(
						analyst,
						HttpMethod.POST,
						"/api/v1/equipment/" + first + "/external-identities",
						"{\"platform\":\"ENERLINK\",\"scope\":\"Sin"
							+ " verificar\",\"externalId\":\"C1\"}")
				.andExpect(status().isCreated());
		verifiedIdentity(first, "Estación Doble", "C1");
		insertVerifiedIdentity(second, "ESTACION DOBLE", "c1");

		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Nadie",
										"C1",
										"1")
								+ enerlink(
										"2",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Sin verificar",
										"C1",
										"1")
								+ enerlink(
										"3",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"estacion doble",
										"C1",
										"1")
								+ enerlink(
										"4",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"",
										"C1",
										"1")
								+ enerlink(
										"5",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"",
										"",
										"1"));

		assertThat(batch(id)).containsEntry("pendingLinkRows", 5).containsEntry("invalidRows", 0);
		assertThat(issueCode(id, "1")).isEqualTo("IDENTITY_NOT_REGISTERED");
		assertThat(issueCode(id, "2")).isEqualTo("IDENTITY_NOT_VERIFIED");
		assertThat(issueCode(id, "3")).isEqualTo("IDENTITY_AMBIGUOUS");
		assertThat(issueCode(id, "4")).isEqualTo("IDENTITY_INCOMPLETE");
		assertThat(issueCode(id, "5")).isEqualTo("IDENTITY_INCOMPLETE");
		confirm(id);

		assertThat(
						jdbc.queryForList(
								"select link_issue from energy_session order by"
									+ " external_session_id",
								String.class))
				.containsExactly(
						"IDENTITY_NOT_REGISTERED",
						"IDENTITY_NOT_VERIFIED",
						"IDENTITY_AMBIGUOUS",
						"IDENTITY_INCOMPLETE",
						"IDENTITY_INCOMPLETE");
		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(jsonPath("$.linked").value(0))
				.andExpect(jsonPath("$.stillPending").value(5));
		assertOfficial("2026-10-21", "2026-10-22", 0, 0.0);
	}

	@Test
	void linkPendingLinksUniqueMatchesLeavesAmbiguousOnesAndAuditsEachSession() throws Exception {
		String first =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-1")));
		String second =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-2")));
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Unica",
										"C1",
										"2")
								+ enerlink(
										"2",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Doble",
										"C1",
										"3")));
		verifiedIdentity(first, "Unica", "C1");
		verifiedIdentity(first, "Doble", "C1");
		insertVerifiedIdentity(second, "DOBLE", "c1");

		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(jsonPath("$.linked").value(1))
				.andExpect(jsonPath("$.stillPending").value(1));

		assertOfficial("2026-10-21", "2026-10-22", 1, 2.0);
		assertThat(
						jdbc.queryForObject(
								"select link_issue from energy_session where external_session_id ="
									+ " '2'",
								String.class))
				.isEqualTo("IDENTITY_AMBIGUOUS");
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'ENERGY_SESSION_LINKED'",
								Integer.class))
				.isEqualTo(1);
	}

	@Test
	void reimportingAPendingSessionLinksItWhenConfirmingTheBatch() throws Exception {
		String station =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-1")));
		String report =
				ENERLINK_HEADER
						+ enerlink(
								"1",
								"21-10-2026 08:00:00",
								"21-10-2026 08:30:00",
								"Activo",
								"C1",
								"2");
		confirm(upload("ENERLINK", "t.csv", report));
		assertThat(batch(upload("ENERLINK", "t.csv", report))).containsEntry("identicalRows", 1);

		verifiedIdentity(station, "Activo", "C1");
		String id = upload("ENERLINK", "t.csv", report);

		assertThat(batch(id)).containsEntry("linkableRows", 1).containsEntry("newRows", 0);
		assertThat(confirm(id)).containsEntry("appliedRows", 1);
		assertThat(rowOutcome(id, "1")).isEqualTo("LINKED");
		assertOfficial("2026-10-21", "2026-10-22", 1, 2.0);
		assertThat(jdbc.queryForObject("select count(*) from energy_session", Integer.class))
				.isEqualTo(1);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'ENERGY_SESSION_LINKED' and actor_id is not null",
								Integer.class))
				.isEqualTo(1);
		assertThat(batch(upload("ENERLINK", "t.csv", report))).containsEntry("identicalRows", 1);
	}

	@Test
	void aLinkThatStoppedBeingUniqueAfterThePreviewIsNotApplied() throws Exception {
		String first =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-1")));
		String second =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-2")));
		verifiedIdentity(first, "Activo", "C1");
		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Activo",
										"C1",
										"2"));
		assertThat(batch(id)).containsEntry("newRows", 1);

		insertVerifiedIdentity(second, "ACTIVO", "c1");
		confirm(id);

		assertOfficial("2026-10-21", "2026-10-22", 0, 0.0);
		assertThat(rowDetail(id, "1")).contains("inventario cambio");
		assertThat(jdbc.queryForObject("select link_issue from energy_session", String.class))
				.isEqualTo("IDENTITY_AMBIGUOUS");
	}

	// ---- sesiones incompletas (feature 002, US3)

	/** Matriz A-E de la especificacion: inicio valido, mismo periodo e identidad verificada. */
	private static String matrix() {
		return ENERLINK_HEADER
				+ enerlink("A", "21-10-2026 08:00:00", "", "Activo", "C1", "")
				+ enerlink("B", "21-10-2026 09:00:00", "21-10-2026 09:30:00", "Activo", "C1", "")
				+ enerlink("C", "21-10-2026 10:00:00", "", "Activo", "C1", "0")
				+ enerlink("D", "21-10-2026 11:00:00", "", "Activo", "C1", "2.500")
				+ enerlink(
						"E", "21-10-2026 12:00:00", "21-10-2026 12:30:00", "Activo", "C1", "1.250");
	}

	@Test
	void theMatrixCountsFiveSessionsAndOnlyKnownEnergyEverywhere() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		String id = upload("ENERLINK", "t.csv", matrix());
		assertThat(batch(id)).containsEntry("newRows", 5).containsEntry("invalidRows", 0);
		confirm(id);

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(5))
				.andExpect(jsonPath("$.official.energyKwh").value(3.75))
				.andExpect(jsonPath("$.official.sessionsWithoutEnergy").value(2))
				.andExpect(jsonPath("$.averageKwhPerSession").value(1.25))
				.andExpect(jsonPath("$.daily[0].energyKwh").value(3.75))
				.andExpect(jsonPath("$.daily[0].sessionsWithoutEnergy").value(2))
				.andExpect(jsonPath("$.top[0].energyKwh").value(3.75))
				.andExpect(jsonPath("$.top[0].sessions").value(5))
				.andExpect(jsonPath("$.equipmentWithoutEnergy").value(0));
		mvc.perform(get("/api/v1/energy/sessions").param("text", "A").cookie(manager))
				.andExpect(
						jsonPath("$.content[?(@.externalSessionId == 'A' && @.energyKwh)]")
								.isEmpty())
				.andExpect(
						jsonPath("$.content[?(@.externalSessionId == 'A' && @.endedAt)]")
								.isEmpty());
		assertThat(
						jdbc.queryForObject(
								"select energy_kwh from energy_session where external_session_id ="
									+ " 'C'",
								java.math.BigDecimal.class))
				.isEqualByComparingTo("0");

		byte[] xlsx =
				mvc.perform(
								get("/api/v1/energy/report")
										.param("format", "XLSX")
										.param("from", "2026-10-21")
										.param("to", "2026-10-22")
										.cookie(manager))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsByteArray();
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(
							valueOf(
									workbook.getSheet("Resumen"),
									"Energ" + (char) 237 + "a conocida (kWh)"))
					.isEqualTo(3.75);
			assertThat(valueOf(workbook.getSheet("Resumen"), "Sesiones")).isEqualTo(5);
			assertThat(
							valueOf(
									workbook.getSheet("Resumen"),
									"Sesiones sin energ" + (char) 237 + "a informada"))
					.isEqualTo(2);
			var cells = new StringBuilder();
			workbook.getSheet("Transacciones")
					.forEach(row -> row.forEach(cell -> cells.append(cell).append("|")));
			assertThat(cells.toString()).contains("No informado");
		}
		mvc.perform(
						get("/api/v1/energy/report")
								.param("format", "PDF")
								.param("from", "2026-10-21")
								.param("to", "2026-10-22")
								.cookie(manager))
				.andExpect(status().isOk());
	}

	@Test
	void aPeriodOnlyWithAbsentEnergyShowsNoDataInsteadOfZero() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", "")));

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(1))
				.andExpect(jsonPath("$.official.energyKwh").doesNotExist())
				.andExpect(jsonPath("$.official.sessionsWithoutEnergy").value(1))
				.andExpect(jsonPath("$.averageKwhPerSession").doesNotExist())
				.andExpect(jsonPath("$.daily[0].energyKwh").doesNotExist())
				.andExpect(jsonPath("$.top.length()").value(0))
				.andExpect(jsonPath("$.equipmentWithoutEnergy").value(1));
		for (String format : List.of("PDF", "XLSX")) {
			mvc.perform(
							get("/api/v1/energy/report")
									.param("format", format)
									.param("from", "2026-10-21")
									.param("to", "2026-10-22")
									.cookie(manager))
					.andExpect(status().isOk());
		}
		mvc.perform(summary("2026-10-25", "2026-10-26").cookie(manager))
				.andExpect(jsonPath("$.official.energyKwh").value(0.0))
				.andExpect(jsonPath("$.averageKwhPerSession").value(0.0));
	}

	@Test
	void anEquipmentWithAKnownZeroStaysInTheRanking() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Activo",
										"C1",
										"0")));

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
				.andExpect(jsonPath("$.official.energyKwh").value(0.0))
				.andExpect(jsonPath("$.top.length()").value(1))
				.andExpect(jsonPath("$.equipmentWithoutEnergy").value(0));
	}

	@Test
	void incompleteSessionsPendingOfIdentityDoNotCountAsOfficial() throws Exception {
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Nadie", "C1", "")));

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(analyst))
				.andExpect(jsonPath("$.official.sessions").value(0))
				.andExpect(jsonPath("$.pendingLink.sessions").value(1))
				.andExpect(jsonPath("$.pendingLink.energyKwh").doesNotExist());
	}

	// ---- reimportacion y correcciones (feature 002, US4)

	@Test
	void reimportingTheMatrixChangesNothing() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(upload("ENERLINK", "t.csv", matrix()));

		for (int attempt = 0; attempt < 2; attempt++) {
			String again = upload("ENERLINK", "t.csv", matrix());
			assertThat(batch(again)).containsEntry("identicalRows", 5).containsEntry("newRows", 0);
			assertThat(confirm(again)).containsEntry("appliedRows", 0);
		}

		assertOfficial("2026-10-21", "2026-10-22", 5, 3.75);
		assertThat(jdbc.queryForObject("select count(*) from energy_session", Integer.class))
				.isEqualTo(5);
	}

	@Test
	void completingASessionNeedsApprovalAndKeepsThePreviousVersion() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", "")));

		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Activo",
										"C1",
										"4"));
		assertThat(batch(id)).containsEntry("modifiedRows", 1);
		decide(id, "1", "\"approved\":true").andExpect(status().isOk());
		confirm(id);

		assertOfficial("2026-10-21", "2026-10-22", 1, 4.0);
		assertThat(
						jdbc.queryForObject(
								"select count(*) from energy_session_revision where energy_kwh is"
									+ " null and ended_at is null",
								Integer.class))
				.isEqualTo(1);
	}

	@Test
	void removingAKnownValueIsShownAndNeedsApproval() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1",
										"21-10-2026 08:00:00",
										"21-10-2026 08:30:00",
										"Activo",
										"C1",
										"4")));

		String unapproved =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", ""));
		assertThat(issueCodes(unapproved, "1"))
				.contains("SESSION_CHANGED", "SESSION_VALUE_REMOVED");
		confirm(unapproved);
		assertOfficial("2026-10-21", "2026-10-22", 1, 4.0);

		String approved =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", ""));
		decide(approved, "1", "\"approved\":true").andExpect(status().isOk());
		confirm(approved);

		assertThat(
						jdbc.queryForObject(
								"select energy_kwh from energy_session",
								java.math.BigDecimal.class))
				.isNull();
		assertThat(
						jdbc.queryForObject(
								"select energy_kwh from energy_session_revision",
								java.math.BigDecimal.class))
				.isEqualByComparingTo("4");
	}

	@Test
	void aPendingSessionThatReceivesItsPairIsCorrectedAndLinkedOnApproval() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER + enerlink("1", "21-10-2026 08:00:00", "", "", "", "3")));

		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", "3"));
		assertThat(batch(id))
				.containsEntry("modifiedRows", 1)
				.containsEntry("equipmentChangedRows", 0);
		decide(id, "1", "\"approved\":true").andExpect(status().isOk());
		confirm(id);

		assertOfficial("2026-10-21", "2026-10-22", 1, 3.0);
		assertThat(jdbc.queryForObject("select link_issue from energy_session", String.class))
				.isNull();
		assertThat(
						jdbc.queryForObject(
								"select count(*) from audit_event where action ="
									+ " 'ENERGY_SESSION_LINKED'",
								Integer.class))
				.isEqualTo(1);
	}

	@Test
	void aNewPairOnALinkedSessionStillRequiresReassignment() throws Exception {
		String station = conectzStationWithEnerlinkIdentity("Activo", "C1");
		verifiedIdentity(station, "Otro activo", "C2");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", "3")));

		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink(
										"1", "21-10-2026 08:00:00", "", "Otro activo", "C2", "3"));

		assertThat(batch(id))
				.containsEntry("equipmentChangedRows", 1)
				.containsEntry("modifiedRows", 0);
		decide(id, "1", "\"approved\":true").andExpect(status().is4xxClientError());
	}

	@Test
	void aWritingVariantOfThePairIsACorrectionAndNotAChangeOfCharger() throws Exception {
		conectzStationWithEnerlinkIdentity("Activo", "C1");
		confirm(
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Activo", "C1", "3")));

		String id =
				upload(
						"ENERLINK",
						"t.csv",
						ENERLINK_HEADER
								+ enerlink("1", "21-10-2026 08:00:00", "", "Áctivo", "c1", "3"));

		assertThat(batch(id))
				.containsEntry("equipmentChangedRows", 0)
				.containsEntry("modifiedRows", 1);
	}

	/**
	 * Forma del incidente con datos ficticios: pares de la plantilla, energia y termino vacios
	 * (SC-007).
	 */
	@Test
	void everyRowOfAnIncidentLikeFileHasAnExplainableResult() throws Exception {
		conectzStationWithEnerlinkIdentity("JAC Uno", "JAC UNO");
		StringBuilder report = new StringBuilder(ENERLINK_HEADER);
		for (int index = 1; index <= 30; index++) {
			report.append(
					enerlink(
							"L" + index,
							"21-10-2026 08:00:00",
							"21-10-2026 08:30:00",
							"JAC UNO",
							"jac uno",
							"1"));
		}
		report.append(
				enerlink(
						"V1",
						"21-10-2026 09:00:00",
						"21-10-2026 09:30:00",
						"JAC Uno",
						"JAC UNO",
						""));
		report.append(enerlink("V2", "21-10-2026 09:00:00", "", "JAC Uno", "JAC UNO", ""));
		report.append(enerlink("P1", "21-10-2026 09:00:00", "", "Desconocido", "X", "1"));
		report.append(enerlink("I1", "", "", "JAC Uno", "JAC UNO", "1"));

		String id = upload("ENERLINK", "incidente.csv", report.toString());

		assertThat(batch(id))
				.containsEntry("totalRows", 34)
				.containsEntry("newRows", 32)
				.containsEntry("pendingLinkRows", 1)
				.containsEntry("invalidRows", 1);
		assertThat(issueCode(id, "P1")).isEqualTo("IDENTITY_NOT_REGISTERED");
		confirm(id);
		assertOfficial("2026-10-21", "2026-10-22", 32, 30.0);
	}

	// ---- helpers

	private static String enerlink(
			String id, String start, String end, String site, String charger, String energy) {
		return String.join("\t", id, start, end, site, charger, "A - CCS1", "80", energy) + "\n";
	}

	/**
	 * Dos equipos con pares equivalentes solo pueden venir de datos previos: se insertan
	 * directamente.
	 */
	private void insertVerifiedIdentity(String equipmentId, String site, String charger) {
		jdbc.update(
				"""
                insert into asset_external_identity(id,equipment_id,platform,scope,external_id,verified,verified_at,
                created_at,updated_at,version) values(gen_random_uuid(),?::uuid,'ENERLINK',?,?,true,now(),now(),now(),0)
                """,
				equipmentId,
				site,
				charger);
	}

	private String rowsOf(String batchId) throws Exception {
		return mvc.perform(get("/api/v1/energy/imports/" + batchId + "/rows").cookie(analyst))
				.andReturn()
				.getResponse()
				.getContentAsString();
	}

	private List<String> issueCodes(String batchId, String externalSessionId) throws Exception {
		return JsonPath.read(
				rowsOf(batchId),
				"$.content[?(@.externalSessionId == '" + externalSessionId + "')].issues[*].code");
	}

	private String issueCode(String batchId, String externalSessionId) throws Exception {
		return issueCodes(batchId, externalSessionId).getFirst();
	}

	private String rowOutcome(String batchId, String externalSessionId) throws Exception {
		return JsonPath.<List<String>>read(
						rowsOf(batchId),
						"$.content[?(@.externalSessionId == '" + externalSessionId + "')].outcome")
				.getFirst();
	}

	private String rowDetail(String batchId, String externalSessionId) throws Exception {
		return JsonPath.<List<String>>read(
						rowsOf(batchId),
						"$.content[?(@.externalSessionId == '"
								+ externalSessionId
								+ "')].outcomeDetail")
				.getFirst();
	}

	private static String wallbox(String serial, String sessionId, String energy) {
		return WALLBOX_HEADER
				+ serial
				+ "\tPlaza\t"
				+ sessionId
				+ "\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t"
				+ energy
				+ "\n";
	}

	private ResultActions decide(String batchId, String externalSessionId, String decision)
			throws Exception {
		return send(
				analyst,
				HttpMethod.PUT,
				"/api/v1/energy/imports/" + batchId + "/rows/decision",
				"{\"rowIds\":[\"" + rowId(batchId, externalSessionId) + "\"]," + decision + "}");
	}

	private String upload(String source, String fileName, String content) throws Exception {
		String id =
				JsonPath.read(
						mvc.perform(
										multipart("/api/v1/energy/imports")
												.file(
														new MockMultipartFile(
																"file",
																fileName,
																"text/csv",
																content.getBytes(
																		StandardCharsets.UTF_8)))
												.param("source", source)
												.with(xsrf())
												.cookie(analyst))
								.andExpect(status().isAccepted())
								.andExpect(jsonPath("$.status").value("PARSING"))
								.andReturn()
								.getResponse()
								.getContentAsString(),
						"$.id");
		awaitStatus(id, "READY");
		return id;
	}

	private Map<String, Object> confirm(String id) throws Exception {
		send(
						analyst,
						HttpMethod.POST,
						"/api/v1/energy/imports/" + id + "/confirm",
						"{\"version\":" + batch(id).get("version") + "}")
				.andExpect(status().isAccepted());
		return awaitStatus(id, "COMPLETED");
	}

	private Map<String, Object> batch(String id) throws Exception {
		return JsonPath.read(
				mvc.perform(get("/api/v1/energy/imports/" + id).cookie(analyst))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsString(),
				"$");
	}

	/** El lote se procesa en segundo plano: se consulta hasta que llega al estado esperado. */
	private Map<String, Object> awaitStatus(String id, String expected) throws Exception {
		long deadline = System.currentTimeMillis() + 20_000;
		Map<String, Object> current = batch(id);
		while (!expected.equals(current.get("status"))) {
			if (!"PARSING".equals(current.get("status"))
					&& !"CONFIRMING".equals(current.get("status"))) {
				fail(
						"El lote quedo en "
								+ current.get("status")
								+ ": "
								+ current.get("failureMessage"));
			}
			if (System.currentTimeMillis() > deadline) {
				fail("El lote no llego a " + expected + "; sigue en " + current.get("status"));
			}
			Thread.sleep(50);
			current = batch(id);
		}
		return current;
	}

	private String rowId(String batchId, String externalSessionId) throws Exception {
		List<String> ids =
				JsonPath.read(
						mvc.perform(
										get("/api/v1/energy/imports/" + batchId + "/rows")
												.cookie(analyst))
								.andReturn()
								.getResponse()
								.getContentAsString(),
						"$.content[?(@.externalSessionId == '" + externalSessionId + "')].id");
		return ids.getFirst();
	}

	private void assertOfficial(String from, String to, int sessions, double energy)
			throws Exception {
		mvc.perform(summary(from, to).cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.official.sessions").value(sessions))
				.andExpect(jsonPath("$.official.energyKwh").value(energy));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
			summary(String from, String to) {
		return get("/api/v1/energy/summary").param("from", from).param("to", to);
	}

	private String conectzStationWithEnerlinkIdentity(String site, String charger)
			throws Exception {
		String station =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment",
								conectzStation("CZ-4")));
		verifiedIdentity(station, site, charger);
		return station;
	}

	private void verifiedIdentity(String equipmentId, String site, String charger)
			throws Exception {
		String identity =
				createdId(
						send(
								analyst,
								HttpMethod.POST,
								"/api/v1/equipment/" + equipmentId + "/external-identities",
								"{\"platform\":\"ENERLINK\",\"scope\":\""
										+ site
										+ "\",\"externalId\":\""
										+ charger
										+ "\"}"));
		send(
						analyst,
						HttpMethod.POST,
						"/api/v1/external-identities/" + identity + "/verify",
						"{\"version\":0}")
				.andExpect(status().isOk());
	}

	private static String conectzStation(String serial) {
		return """
        {"network":"CONECTZ","type":"CHARGING_STATION","brand":"BENNY","name":"Estacion %s","serial":"%s",
         "connectorTypeIds":["%s"]}
        """
				.formatted(serial, serial, CCS2);
	}

	private static double valueOf(Sheet sheet, String label) {
		for (Row row : sheet) {
			if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
				return row.getCell(1).getNumericCellValue();
			}
		}
		throw new AssertionError("No se encontro " + label);
	}

	private void seedUser(String email, Role role) {
		UserAccount user =
				UserAccount.create(
						new Email(email),
						"Usuario " + email,
						Set.of(role),
						encoder.encode(PASSWORD));
		user.changePassword(encoder.encode(PASSWORD));
		users.save(user);
	}

	private ResultActions send(Cookie session, HttpMethod method, String path, String json)
			throws Exception {
		return mvc.perform(
				request(method, path)
						.with(xsrf())
						.cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json));
	}

	private static String createdId(ResultActions result) throws Exception {
		return JsonPath.read(
				result.andExpect(status().isCreated())
						.andReturn()
						.getResponse()
						.getContentAsString(),
				"$.id");
	}
}
