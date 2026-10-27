package com.tidsec.sarc_bakend.modules.consumption.api;

import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
	private static final String WALLBOX_HEADER = "Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n";
	private static final String ENERLINK_HEADER = "ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\t"
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
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user, "
				+ "energy_import_row, energy_session_revision, energy_session, energy_import_batch, "
				+ "asset_external_identity, asset_schedule_interval, asset_schedule_exception, asset_connector, "
				+ "asset_equipment, report_issued CASCADE");
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
				.andExpect(jsonPath("$.daily.length()").value(31))
				.andExpect(jsonPath("$.byNetwork.length()").value(3))
				.andExpect(jsonPath("$.byNetwork[?(@.network == 'KIA')].energyKwh").value(40.0))
				.andExpect(jsonPath("$.byNetwork[?(@.network == 'JAC')].energyKwh").value(0))
				.andExpect(jsonPath("$.bySource[?(@.source == 'WALLBOX')].sessions").value(2))
				.andExpect(jsonPath("$.byType[?(@.type == 'CHARGING_POINT')].energyKwh").value(40.0));

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

	// ---- regresiones de la revision QA del 26-oct-2026

	@Test
	void aSessionThatChangesChargerIsOnlyMovedByAnExplicitReassignment() throws Exception {
		// QA-01: aprobar la correccion no basta; mover la sesion a otro equipo es una decision aparte.
		String secondPoint = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto Norte","serial":"WB-2002",
				 "assignedCode":"204519","uidPuk":"PUK-2","usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(TYPE2)));
		confirm(upload("WALLBOX", "r1.csv", wallbox("WB-1001", "S-1", "10,00")));

		String ignored = upload("WALLBOX", "r2.csv", wallbox("WB-2002", "S-1", "12,00"));
		assertThat(batch(ignored)).containsEntry("equipmentChangedRows", 1).containsEntry("modifiedRows", 0);
		decide(ignored, "S-1", "\"approved\":true").andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ROW_APPROVAL_NOT_REQUIRED"));
		assertThat(confirm(ignored)).containsEntry("appliedRows", 0);
		assertThat(jdbc.queryForObject("select source_serial from energy_session", String.class)).isEqualTo("WB-1001");

		String reassigned = upload("WALLBOX", "r3.csv", wallbox("WB-2002", "S-1", "12,00"));
		decide(reassigned, "S-1", "\"reassign\":true").andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reassigned").value(true));
		assertThat(confirm(reassigned)).containsEntry("appliedRows", 1);
		assertThat(jdbc.queryForMap("select source_serial, equipment_id::text as equipment, revision from energy_session"))
				.containsEntry("source_serial", "WB-2002").containsEntry("equipment", secondPoint)
				.containsEntry("revision", 2);
		assertThat(jdbc.queryForObject("select equipment_id::text from energy_session_revision", String.class))
				.isEqualTo(kiaPoint);
		assertThat(jdbc.queryForObject("select outcome from energy_import_row where batch_id = ?::uuid",
				String.class, reassigned)).isEqualTo("REASSIGNED");
	}

	@Test
	void anApprovalGivenAgainstAnOlderRevisionDoesNotOverwriteANewerCorrection() throws Exception {
		// QA-02: dos lotes revisados sobre la revision 1; el segundo no puede pisar lo que aplico el primero.
		confirm(upload("WALLBOX", "r1.csv", wallbox("WB-1001", "S-1", "10,00")));
		String first = upload("WALLBOX", "a.csv", wallbox("WB-1001", "S-1", "12,00"));
		String second = upload("WALLBOX", "b.csv", wallbox("WB-1001", "S-1", "15,00"));
		decide(first, "S-1", "\"approved\":true").andExpect(status().isOk());
		decide(second, "S-1", "\"approved\":true").andExpect(status().isOk());

		assertThat(confirm(first)).containsEntry("appliedRows", 1);
		assertThat(confirm(second)).containsEntry("appliedRows", 0);

		assertOfficial("2026-10-21", "2026-10-22", 1, 12.0);
		assertThat(jdbc.queryForMap("select outcome, outcome_detail from energy_import_row where batch_id = ?::uuid",
				second)).containsEntry("outcome", "SKIPPED").extractingByKey("outcome_detail").asString()
				.contains("vista previa");
	}

	@Test
	void aNonPreferredSourceLinkedLaterWaitsForApproval() throws Exception {
		// QA-03: Wallbox de una estacion Conectz queda pendiente; al vincularla tampoco suma sin aprobacion.
		confirm(upload("WALLBOX", "pending.csv", wallbox("WB-LATE", "S-1", "10,00")));
		createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", conectzStation("WB-LATE")));

		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/link-pending", "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.linked").value(0))
				.andExpect(jsonPath("$.awaitingApproval").value(1))
				.andExpect(jsonPath("$.stillPending").value(0));
		mvc.perform(summary("2026-10-21", "2026-10-22").cookie(manager))
				.andExpect(jsonPath("$.official.sessions").value(0))
				.andExpect(jsonPath("$.awaitingApproval.sessions").value(1))
				.andExpect(jsonPath("$.awaitingApproval.energyKwh").value(10.0));

		String session = mvc.perform(get("/api/v1/energy/sessions").cookie(analyst))
				.andExpect(jsonPath("$.content[0].state").value("AWAITING_APPROVAL"))
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(session, "$.content[0].id");
		int version = JsonPath.read(session, "$.content[0].version");
		send(manager, HttpMethod.POST, "/api/v1/energy/sessions/" + id + "/source-approval",
				"{\"version\":" + version + "}").andExpect(status().isForbidden());
		send(analyst, HttpMethod.POST, "/api/v1/energy/sessions/" + id + "/source-approval",
				"{\"version\":" + version + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("VALIDATED"));
		assertOfficial("2026-10-21", "2026-10-22", 1, 10.0);
	}

	@Test
	void anOverlongValueInvalidatesOnlyItsRow() throws Exception {
		// QA-07: la serie va en VARCHAR(80); antes de esta validacion abortaba todo el lote al guardar.
		String id = upload("WALLBOX", "long.csv", wallbox("X".repeat(81), "S-1", "10,00")
				+ "WB-1001\tPlaza\t" + "Y".repeat(101) + "\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t5,00\n"
				+ "WB-1001\tPlaza\tVALID\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t5,00\n");

		assertThat(batch(id)).containsEntry("invalidRows", 2).containsEntry("newRows", 1);
		assertThat(confirm(id)).containsEntry("appliedRows", 1);
	}

	@Test
	void reportSummaryAndDetailComeFromTheSameSnapshot() throws Exception {
		// QA-05: una correccion confirmada mientras se genera el reporte no puede descuadrarlo.
		confirm(upload("WALLBOX", "r.csv", wallbox("WB-1001", "S-1", "10,00")));
		AtomicBoolean injected = new AtomicBoolean();
		doAnswer(invocation -> {
			if (injected.compareAndSet(false, true)) {
				Thread writer = new Thread(() -> jdbc.update("update energy_session set energy_kwh = 20, "
						+ "revision = revision + 1, version = version + 1"));
				writer.start();
				writer.join();
			}
			return invocation.callRealMethod();
		}).when(sessionRepository).findAll(any(SessionCriteria.class));

		byte[] xlsx = mvc.perform(get("/api/v1/energy/report").param("format", "XLSX")
				.param("from", "2026-10-21").param("to", "2026-10-22").cookie(manager))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

		assertThat(injected).isTrue();
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(valueOf(workbook.getSheet("Resumen"), "Energía (kWh)")).isEqualTo(10.0);
			assertThat(workbook.getSheet("Transacciones").getRow(5).getCell(5).getNumericCellValue()).isEqualTo(10.0);
		}
	}

	@Test
	void everyIssuedReportIsArchivedWithItsChecksumAndCanBeDownloadedAgain() throws Exception {
		// Lectura-01 (RF-RPT-08): se conserva la copia exacta que se entrego.
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenReturn("sarc/reports/copia");
		when(fileStorage.documentUrl("sarc/reports/copia")).thenReturn(Optional.of("https://files.test/firmada"));
		byte[] pdf = mvc.perform(get("/api/v1/energy/report").param("format", "PDF")
				.param("from", "2026-10-01").param("to", "2026-11-01").cookie(manager))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

		String issued = mvc.perform(get("/api/v1/reports/issued").param("kind", "CONSUMPTION").cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].format").value("PDF"))
				.andExpect(jsonPath("$.content[0].archived").value(true))
				.andExpect(jsonPath("$.content[0].sizeBytes").value(pdf.length))
				.andExpect(jsonPath("$.content[0].checksum").value(HexFormat.of()
						.formatHex(MessageDigest.getInstance("SHA-256").digest(pdf))))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(get("/api/v1/reports/issued/" + JsonPath.read(issued, "$.content[0].id") + "/download")
				.cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://files.test/firmada"));
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'CONSUMPTION_REPORT_GENERATED' "
				+ "and entity_type = 'ISSUED_REPORT'", Integer.class)).isEqualTo(1);
	}

	@Test
	void aBatchRejectedByAFullQueueIsLeftFailedInsteadOfParsingForever() throws Exception {
		// QA-06: sin trabajador que lo tome, el lote se marca FAILED en una transaccion propia.
		CountDownLatch busy = new CountDownLatch(IMPORT_THREADS);
		CountDownLatch release = new CountDownLatch(1);
		Runnable blocking = () -> {
			busy.countDown();
			try {
				release.await(20, TimeUnit.SECONDS);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		};
		try {
			for (int i = 0; i < IMPORT_THREADS; i++) {
				importJobs.submitAfterCommit(blocking, () -> fail("Rechazo inesperado"));
			}
			assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();
			for (int i = 0; i < IMPORT_QUEUE; i++) {
				importJobs.submitAfterCommit(() -> { }, () -> fail("Rechazo inesperado"));
			}
			mvc.perform(multipart("/api/v1/energy/imports")
					.file(new MockMultipartFile("file", "cola.csv", "text/csv",
							wallbox("WB-1001", "S-1", "1,00").getBytes(StandardCharsets.UTF_8)))
					.param("source", "WALLBOX").with(xsrf()).cookie(analyst));

			assertThat(jdbc.queryForObject("select status from energy_import_batch", String.class)).isEqualTo("FAILED");
		}
		finally {
			release.countDown();
		}
	}

	// ---- helpers

	private static String wallbox(String serial, String sessionId, String energy) {
		return WALLBOX_HEADER + serial + "\tPlaza\t" + sessionId + "\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t"
				+ energy + "\n";
	}

	private ResultActions decide(String batchId, String externalSessionId, String decision) throws Exception {
		return send(analyst, HttpMethod.PUT, "/api/v1/energy/imports/" + batchId + "/rows/decision",
				"{\"rowIds\":[\"" + rowId(batchId, externalSessionId) + "\"]," + decision + "}");
	}

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
