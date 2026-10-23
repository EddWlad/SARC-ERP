package com.tidsec.sarc_bakend.modules.consumption.api;

import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Flujo completo de RF-003 contra PostgreSQL: cargar, revisar, confirmar, consultar e informar. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumptionApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";
	private static final String TYPE2 = "00000000-0000-0000-0000-000000000c01";
	private static final String CCS2 = "00000000-0000-0000-0000-000000000c02";
	private static final String WALLBOX_HEADER = "Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n";
	private static final String ENERLINK_HEADER = "ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\t"
			+ "CONECTOR\tSOC FINAL (%)\tENERGIA CARGADA (kWh)\n";

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage fileStorage;

	private Cookie analyst;
	private Cookie manager;
	private String kiaPoint;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user, "
				+ "energy_import_row, energy_session_revision, energy_session, energy_import_batch, "
				+ "asset_external_identity, asset_schedule_interval, asset_schedule_exception, asset_connector, "
				+ "asset_equipment CASCADE");
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenReturn("sarc/imports/original");
		seedUser("analista@eos.com", Role.ANALISTA);
		seedUser("gerente@eos.com", Role.GERENTE);
		analyst = login(mvc, "analista@eos.com", PASSWORD);
		manager = login(mvc, "gerente@eos.com", PASSWORD);
		kiaPoint = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto Quicentro","serial":"wb-1001",
				 "assignedCode":"204518","uidPuk":"PUK-1","usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(TYPE2)));
	}

	// ---- idempotencia y formatos (CA-003-01, CA-003-02)

	@Test
	void reimportingTheSameFileAddsNeitherSessionsNorEnergy() throws Exception {
		String report = WALLBOX_HEADER
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
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'ENERGY_IMPORT_COMPLETED' "
				+ "and actor_id is not null", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from energy_session where created_by is null", Integer.class))
				.isZero();
	}

	// ---- dia de conteo y red oficial (CA-003-03, CA-003-04)

	@Test
	void sessionIsCountedOnItsStartDayAndItsNetworkComesFromTheInventory() throws Exception {
		String station = conectzStationWithEnerlinkIdentity("Conectz", "ESTACION 4");

		String id = upload("ENERLINK", "Transacciones_KIA.csv", ENERLINK_HEADER
				+ "27151\t20-10-2026 23:30:00\t21-10-2026 00:10:00\tConectz\tESTACION 4\tA - CCS1\t80\t22.42\n");
		confirm(id);

		mvc.perform(get("/api/v1/energy/sessions").cookie(manager))
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
		String id = upload("ENERLINK", "transacciones.csv", ENERLINK_HEADER
				+ "500\t21-10-2026 08:00:00\t21-10-2026 08:30:00\tConectz\tESTACION 4\tA - CCS1\t80\t10\n"
				+ "501\t21-10-2026 09:00:00\t21-10-2026 09:30:00\tConectz\tESTACION 4\tA - CCS1\t80\t5\n");
		String testRow = rowId(id, "501");

		send(analyst, HttpMethod.PUT, "/api/v1/energy/imports/" + id + "/rows/decision",
				"{\"rowIds\":[\"" + testRow + "\"],\"test\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].test").value(true));
		confirm(id);

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(1))
				.andExpect(jsonPath("$.official.energyKwh").value(10.0))
				.andExpect(jsonPath("$.test.sessions").value(1))
				.andExpect(jsonPath("$.test.energyKwh").value(5.0));
	}

	// ---- conciliacion de equipos desconocidos (RF-003.7)

	@Test
	void unknownChargerStaysPendingUntilItsIdentityIsVerified() throws Exception {
		String station = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", conectzStation("CZ-9")));
		String id = upload("ENERLINK", "transacciones.csv", ENERLINK_HEADER
				+ "900\t21-10-2026 08:00:00\t21-10-2026 08:30:00\tConectz Aeropuerto\tAEROPUERTO 1\t1 - CCS2\t80\t7.5\n");
		assertThat(batch(id)).containsEntry("pendingLinkRows", 1);
		confirm(id);

		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
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
		confirm(upload("WALLBOX", "r1.csv", WALLBOX_HEADER
				+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t10,00\n"));
		String changed = WALLBOX_HEADER + "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:10:00\t12,50\n";

		String unapproved = upload("WALLBOX", "r2.csv", changed);
		assertThat(batch(unapproved)).containsEntry("modifiedRows", 1);
		assertThat(confirm(unapproved)).containsEntry("appliedRows", 0);
		assertOfficial("2026-10-21", "2026-10-22", 1, 10.0);

		String approved = upload("WALLBOX", "r3.csv", changed);
		send(analyst, HttpMethod.PUT, "/api/v1/energy/imports/" + approved + "/rows/decision",
				"{\"rowIds\":[\"" + rowId(approved, "S-1") + "\"],\"approved\":true}").andExpect(status().isOk());
		assertThat(confirm(approved)).containsEntry("appliedRows", 1);
		assertOfficial("2026-10-21", "2026-10-22", 1, 12.5);
		assertThat(jdbc.queryForObject("select count(*) from energy_session_revision", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select revision from energy_session", Integer.class)).isEqualTo(2);
	}

	// ---- errores de interpretacion

	@Test
	void invalidRowsAreReportedAndOnlyValidOnesAreImported() throws Exception {
		String id = upload("WALLBOX", "r.csv", WALLBOX_HEADER
				+ "WB-1001\tPlaza\tS-1\t31/02/2026 08:00:00\t2026-10-21 09:00:00\t1,5\n"
				+ "WB-1001\tPlaza\tS-2\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t1,5\n");

		assertThat(batch(id)).containsEntry("invalidRows", 1).containsEntry("newRows", 1);
		mvc.perform(get("/api/v1/energy/imports/" + id + "/rows").param("classification", "INVALID").cookie(analyst))
				.andExpect(jsonPath("$.content[0].rowNumber").value(2))
				.andExpect(jsonPath("$.content[0].issues[0].code").value("INVALID_DATE"));
		assertThat(confirm(id)).containsEntry("appliedRows", 1);
	}

	@Test
	void aFileFromAnotherPlatformFailsWithAReadableReason() throws Exception {
		String id = JsonPath.read(mvc.perform(multipart("/api/v1/energy/imports")
				.file(new MockMultipartFile("file", "x.csv", "text/csv", (WALLBOX_HEADER
						+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t1\n").getBytes(StandardCharsets.UTF_8)))
				.param("source", "ENERLINK").with(xsrf()).cookie(analyst))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");

		Map<String, Object> failed = awaitStatus(id, "FAILED");
		assertThat(failed).containsEntry("failureCode", "IMPORT_COLUMNS_MISSING");
	}

	// ---- reportes y permisos (CA-003-05, PRD 4)

	@Test
	void reportTotalsMatchTheDashboard() throws Exception {
		confirm(upload("WALLBOX", "r.csv", WALLBOX_HEADER
				+ "WB-1001\tPlaza\tS-1\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t35,41\n"
				+ "WB-1001\tPlaza\tS-2\t2026-10-22 08:00:00\t2026-10-22 09:00:00\t4,59\n"));

		mvc.perform(summary("2026-10-01", "2026-11-01").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(2))
				.andExpect(jsonPath("$.official.energyKwh").value(40.0))
				.andExpect(jsonPath("$.official.equipment").value(1))
				.andExpect(jsonPath("$.top[0].equipment.id").value(kiaPoint))
				.andExpect(jsonPath("$.daily.length()").value(31));

		byte[] xlsx = mvc.perform(get("/api/v1/energy/report").param("format", "XLSX")
				.param("from", "2026-10-01").param("to", "2026-11-01").cookie(manager))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet summary = workbook.getSheet("Resumen");
			assertThat(valueOf(summary, "Energía (kWh)")).isEqualTo(40.0);
			assertThat(valueOf(summary, "Sesiones")).isEqualTo(2.0);
			assertThat(workbook.getSheet("Transacciones").getLastRowNum()).isEqualTo(6);
		}
		byte[] pdf = mvc.perform(get("/api/v1/energy/report").param("format", "PDF")
				.param("from", "2026-10-01").param("to", "2026-11-01").cookie(manager))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	@Test
	void everyoneConsultsButOnlyEditorsImport() throws Exception {
		mvc.perform(summary("2026-10-01", "2026-11-01").cookie(manager)).andExpect(status().isOk());
		mvc.perform(get("/api/v1/energy/sessions").cookie(manager)).andExpect(status().isOk());
		mvc.perform(multipart("/api/v1/energy/imports").file(new MockMultipartFile("file", "x.csv", "text/csv",
				WALLBOX_HEADER.getBytes(StandardCharsets.UTF_8))).param("source", "WALLBOX").with(xsrf()).cookie(manager))
				.andExpect(status().isForbidden());
		send(manager, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "").andExpect(status().isForbidden());
		mvc.perform(summary("2026-10-05", "2026-10-01").cookie(manager))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
	}

	// ---- helpers

	private String upload(String source, String fileName, String content) throws Exception {
		String id = JsonPath.read(mvc.perform(multipart("/api/v1/energy/imports")
				.file(new MockMultipartFile("file", fileName, "text/csv", content.getBytes(StandardCharsets.UTF_8)))
				.param("source", source).with(xsrf()).cookie(analyst))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("PARSING"))
				.andReturn().getResponse().getContentAsString(), "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	private Map<String, Object> confirm(String id) throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/energy/imports/" + id + "/confirm",
				"{\"version\":" + batch(id).get("version") + "}")
				.andExpect(status().isAccepted());
		return awaitStatus(id, "COMPLETED");
	}

	private Map<String, Object> batch(String id) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/energy/imports/" + id).cookie(analyst))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$");
	}

	/** El lote se procesa en segundo plano: se consulta hasta que llega al estado esperado. */
	private Map<String, Object> awaitStatus(String id, String expected) throws Exception {
		long deadline = System.currentTimeMillis() + 20_000;
		Map<String, Object> current = batch(id);
		while (!expected.equals(current.get("status"))) {
			if (!"PARSING".equals(current.get("status")) && !"CONFIRMING".equals(current.get("status"))) {
				fail("El lote quedo en " + current.get("status") + ": " + current.get("failureMessage"));
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
		List<String> ids = JsonPath.read(mvc.perform(get("/api/v1/energy/imports/" + batchId + "/rows").cookie(analyst))
				.andReturn().getResponse().getContentAsString(),
				"$.content[?(@.externalSessionId == '" + externalSessionId + "')].id");
		return ids.getFirst();
	}

	private void assertOfficial(String from, String to, int sessions, double energy) throws Exception {
		mvc.perform(summary(from, to).cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.official.sessions").value(sessions))
				.andExpect(jsonPath("$.official.energyKwh").value(energy));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder summary(String from,
			String to) {
		return get("/api/v1/energy/summary").param("from", from).param("to", to);
	}

	private String conectzStationWithEnerlinkIdentity(String site, String charger) throws Exception {
		String station = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", conectzStation("CZ-4")));
		verifiedIdentity(station, site, charger);
		return station;
	}

	private void verifiedIdentity(String equipmentId, String site, String charger) throws Exception {
		String identity = createdId(send(analyst, HttpMethod.POST,
				"/api/v1/equipment/" + equipmentId + "/external-identities",
				"{\"platform\":\"ENERLINK\",\"scope\":\"" + site + "\",\"externalId\":\"" + charger + "\"}"));
		send(analyst, HttpMethod.POST, "/api/v1/external-identities/" + identity + "/verify", "{\"version\":0}")
				.andExpect(status().isOk());
	}

	private static String conectzStation(String serial) {
		return """
				{"network":"CONECTZ","type":"CHARGING_STATION","brand":"BENNY","name":"Estacion %s","serial":"%s",
				 "connectorTypeIds":["%s"]}
				""".formatted(serial, serial, CCS2);
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
		UserAccount user = UserAccount.create(new Email(email), "Usuario " + email, Set.of(role),
				encoder.encode(PASSWORD));
		user.changePassword(encoder.encode(PASSWORD));
		users.save(user);
	}

	private ResultActions send(Cookie session, HttpMethod method, String path, String json) throws Exception {
		return mvc.perform(request(method, path)
				.with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String createdId(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
				"$.id");
	}
}
