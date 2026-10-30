package com.tidsec.sarc_bakend.modules.inventory.api;

import static com.tidsec.sarc_bakend.support.WebSessions.login;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.Cookie;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";
	/** Tipos de conector sembrados por la migracion V004. */
	private static final String TYPE2 = "00000000-0000-0000-0000-000000000c01";
	private static final String CCS2 = "00000000-0000-0000-0000-000000000c02";
	private static final String CHADEMO = "00000000-0000-0000-0000-000000000c03";
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage fileStorage;
	@MockitoSpyBean EquipmentRepository equipmentRepository;

	private Cookie analyst;
	private Cookie manager;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user, "
				+ "asset_external_identity, asset_schedule_interval, asset_schedule_exception, asset_connector, "
				+ "asset_equipment, report_issued CASCADE");
		jdbc.update("DELETE FROM asset_connector_type WHERE id::text NOT LIKE '00000000-0000-0000-0000-000000000c%'");
		seedUser("analista@eos.com", Role.ANALISTA);
		seedUser("gerente@eos.com", Role.GERENTE);
		analyst = login(mvc, "analista@eos.com", PASSWORD);
		manager = login(mvc, "gerente@eos.com", PASSWORD);
	}

	// ---- reglas de alta por red y tipo

	@Test
	void registersAKiaPointWithItsSixDigitCode() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-1"))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.assignedCode").value("123456"))
				.andExpect(jsonPath("$.serial").value("KIA-001"))
				.andExpect(jsonPath("$.uidPuk").value("UID-1"))
				.andExpect(jsonPath("$.usageStatus").value("FREE"))
				.andExpect(jsonPath("$.adminStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.connectors.length()").value(1))
				.andExpect(jsonPath("$.connectors[0].connectorTypeCode").value("TYPE2"))
				.andExpect(jsonPath("$.schedule.mode").value("NOT_DEFINED"))
				.andExpect(jsonPath("$.restrictedDataVisible").value(true))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void rejectsKiaCodesStartingWithZeroAndPointsWithTwoConnectors() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "012345", "UID-1"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_ASSIGNED_CODE"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"P","serial":"KIA-002",
				 "assignedCode":"223456","uidPuk":"UID-2","usageStatus":"FREE","connectorTypeIds":["%s","%s"]}
				""".formatted(TYPE2, CCS2))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_CONNECTOR_COUNT"));
	}

	@Test
	void jacPointUsesItsSerialAsCodeAndRequiresUidPuk() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", jacPoint("jac-77", null))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("UID_PUK_REQUIRED"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", jacPoint("jac-77", "UID-77"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignedCode").value("JAC-77"));
	}

	@Test
	void stationAcceptsUpToThreeConnectorsWithoutUidPukOrUsageStatus() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.assignedCode").value("EST-1"))
				.andExpect(jsonPath("$.uidPuk").doesNotExist())
				.andExpect(jsonPath("$.usageStatus").doesNotExist())
				.andExpect(jsonPath("$.connectors.length()").value(3));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"CONECTZ","type":"CHARGING_STATION","brand":"BENNY","name":"E","serial":"EST-2",
				 "usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(CCS2))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("USAGE_STATUS_NOT_APPLICABLE"));
	}

	// ---- unicidad global, baja logica y recuperacion

	@Test
	void duplicatedIdentifiersConflictAndDeletedOnesOfferRecovery() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-1")));

		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-002", "123456", "UID-2"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EQUIPMENT_IDENTIFIER_IN_USE"))
				.andExpect(jsonPath("$.equipmentId").value(id));

		send(analyst, HttpMethod.PATCH, "/api/v1/equipment/" + id + "/status", "{\"version\":0,\"status\":\"DELETED\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adminStatus").value("DELETED"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("kia-001", "999999", "UID-9"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EQUIPMENT_DELETED_EXISTS"))
				.andExpect(jsonPath("$.equipmentId").value(id));

		send(analyst, HttpMethod.PATCH, "/api/v1/equipment/" + id + "/status", "{\"version\":1,\"status\":\"ACTIVE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.adminStatus").value("ACTIVE"));
	}

	@Test
	void editingTheSerialToOneInUseIsAReadableConflict() throws Exception {
		String taken = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-2")));

		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, """
				{"version":0,"brand":"BENNY","name":"E","serial":"EST-1","connectorTypeIds":["%s"]}
				""".formatted(CCS2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EQUIPMENT_IDENTIFIER_IN_USE"))
				.andExpect(jsonPath("$.equipmentId").value(taken));
	}

	@Test
	void identifiersOfTwoDifferentEquipmentAreNeverMerged() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-1"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-002", "223456", "UID-2"));

		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "223456", "UID-3"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDENTIFIERS_BELONG_TO_DIFFERENT_EQUIPMENT"))
				.andExpect(jsonPath("$.equipmentIds.length()").value(2));
	}

	@Test
	void listingHidesDeletedEquipmentUnlessAskedAndFilters() throws Exception {
		String kia = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-1")));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1"));
		send(analyst, HttpMethod.PATCH, "/api/v1/equipment/" + kia + "/status", "{\"version\":0,\"status\":\"DELETED\"}");

		mvc.perform(get("/api/v1/equipment").cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].serial").value("EST-1"))
				.andExpect(jsonPath("$.content[0].connectorTypeCodes.length()").value(3));
		mvc.perform(get("/api/v1/equipment").cookie(manager).param("status", "DELETED"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(kia));
		mvc.perform(get("/api/v1/equipment").cookie(manager).param("network", "JAC").param("text", "est"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	// ---- permisos y proyeccion por rol

	@Test
	void managerSeesInventoryWithoutRestrictedDataAndCannotEdit() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto","serial":"KIA-001",
				 "assignedCode":"123456","uidPuk":"UID-1","ocppUrl":"wss://ocpp.example/KIA-001",
				 "usageStatus":"BLOCKED","connectorTypeIds":["%s"]}
				""".formatted(TYPE2)));

		mvc.perform(get("/api/v1/equipment/" + id).cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uidPuk").doesNotExist())
				.andExpect(jsonPath("$.ocppUrl").doesNotExist())
				.andExpect(jsonPath("$.restrictedDataVisible").value(false));
		mvc.perform(get("/api/v1/equipment/" + id).cookie(analyst))
				.andExpect(jsonPath("$.ocppUrl").value("wss://ocpp.example/KIA-001"));
		send(manager, HttpMethod.POST, "/api/v1/equipment", station("EST-9"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	// ---- edicion, conectores y horario

	@Test
	void updateCorrectsSerialKeepsIdentityAndRetiresExtraConnectors() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));
		String update = """
				{"version":%d,"brand":"BENNY","name":"Electrolinera Norte","serial":"EST-1B",
				 "connectorTypeIds":["%s","%s"]}
				""";

		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, update.formatted(5, CHADEMO, CCS2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, update.formatted(0, CHADEMO, CCS2))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.serial").value("EST-1B"))
				.andExpect(jsonPath("$.assignedCode").value("EST-1B"))
				.andExpect(jsonPath("$.connectors.length()").value(2))
				.andExpect(jsonPath("$.connectors[0].connectorTypeCode").value("CHADEMO"));

		assertThat(jdbc.queryForObject("select count(*) from asset_connector where equipment_id = ?::uuid "
				+ "and not active", Long.class, id)).isEqualTo(1);
		assertThat(auditActions(id)).containsExactly("EQUIPMENT_REGISTERED", "EQUIPMENT_SERIAL_CORRECTED");
	}

	@Test
	void weeklyScheduleWithOvernightIntervalAndExceptions() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));

		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule", """
				{"version":0,"mode":"WEEKLY",
				 "intervals":[{"day":"FRIDAY","opensAt":"20:00","closesAt":"02:00"},
				              {"day":"SATURDAY","opensAt":"01:00","closesAt":"08:00"}],
				 "exceptions":[]}
				""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SCHEDULE_INTERVALS_OVERLAP"));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule", """
				{"version":0,"mode":"WEEKLY",
				 "intervals":[{"day":"MONDAY","opensAt":"08:00","closesAt":"18:00"},
				              {"day":"FRIDAY","opensAt":"20:00","closesAt":"02:00"}],
				 "exceptions":[{"date":"2026-12-25","closed":true,"note":"Navidad"},
				               {"date":"2026-12-31","closed":false,"opensAt":"08:00","closesAt":"12:00"}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.schedule.mode").value("WEEKLY"))
				.andExpect(jsonPath("$.schedule.intervals.length()").value(2))
				.andExpect(jsonPath("$.schedule.intervals[1].overnight").value(true))
				.andExpect(jsonPath("$.schedule.exceptions[0].closed").value(true))
				.andExpect(jsonPath("$.schedule.exceptions[1].opensAt").value("08:00:00"));
	}

	// ---- catalogo de conectores

	@Test
	void connectorTypeCatalogIsExtensibleAndInactiveTypesAreNotAssignable() throws Exception {
		mvc.perform(get("/api/v1/connector-types").cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(6));
		String created = send(analyst, HttpMethod.POST, "/api/v1/connector-types", "{\"code\":\"nacs\",\"name\":\"NACS\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("NACS"))
				.andReturn().getResponse().getContentAsString();
		String typeId = JsonPath.read(created, "$.id");
		send(analyst, HttpMethod.POST, "/api/v1/connector-types", "{\"code\":\"NACS\",\"name\":\"Otro\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONNECTOR_TYPE_CODE_EXISTS"));
		send(analyst, HttpMethod.PATCH, "/api/v1/connector-types/" + typeId, "{\"version\":0,\"status\":\"INACTIVE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INACTIVE"));

		send(analyst, HttpMethod.POST, "/api/v1/equipment", """
				{"network":"JAC","type":"CHARGING_STATION","brand":"BENNY","name":"E","serial":"EST-5",
				 "connectorTypeIds":["%s"]}
				""".formatted(typeId))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("CONNECTOR_TYPE_INACTIVE"));
	}

	// ---- identidades externas

	@Test
	void externalIdentitiesResolveVerifyAndNeverPointToTwoEquipment() throws Exception {
		String first = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));
		String second = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-2")));
		String identity = """
				{"platform":"ENERLINK","scope":"cuenta-1","externalId":"ENL-55","alias":"Estacion norte"}
				""";

		String created = send(analyst, HttpMethod.POST, "/api/v1/equipment/" + first + "/external-identities", identity)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verified").value(false))
				.andReturn().getResponse().getContentAsString();
		String identityId = JsonPath.read(created, "$.id");
		send(analyst, HttpMethod.POST, "/api/v1/equipment/" + second + "/external-identities", identity)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXTERNAL_IDENTITY_IN_USE"))
				.andExpect(jsonPath("$.equipmentId").value(first));

		mvc.perform(get("/api/v1/external-identities/resolve").cookie(manager)
				.param("platform", "ENERLINK").param("scope", "cuenta-1").param("externalId", " ENL-55 "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.equipmentId").value(first));

		send(analyst, HttpMethod.POST, "/api/v1/external-identities/" + identityId + "/verify", "{\"version\":0}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verified").value(true))
				.andExpect(jsonPath("$.verifiedAt").exists());
		mvc.perform(delete("/api/v1/external-identities/" + identityId).with(xsrf()).cookie(analyst))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EXTERNAL_IDENTITY_VERIFIED"));
	}

	// ---- imagen

	@Test
	void imagesAreValidatedStoredPrivatelyAndReplaced() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));
		when(fileStorage.store(eq("equipment/" + id), any())).thenReturn("sarc/equipment/" + id + "/foto");
		when(fileStorage.accessUrl(anyString())).thenReturn(Optional.of("https://res.example/signed/foto"));

		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/equipment/" + id + "/image")
				.file(new MockMultipartFile("file", "foto.pdf", "image/png", "%PDF-1.7".getBytes()))
				.param("version", "0").with(xsrf()).cookie(analyst))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
		mvc.perform(multipart(HttpMethod.PUT, "/api/v1/equipment/" + id + "/image")
				.file(new MockMultipartFile("file", "foto.png", "image/png", PNG))
				.param("version", "0").with(xsrf()).cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").value("https://res.example/signed/foto"));

		mvc.perform(delete("/api/v1/equipment/" + id + "/image").param("version", "1").with(xsrf()).cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").doesNotExist());
		verify(fileStorage).delete("sarc/equipment/" + id + "/foto");
	}

	// ---- tablero y reportes

	@Test
	void summaryCountsEveryCategoryAndExcludesDeletedEquipment() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-1"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", jacPoint("JAC-1", "UID-2"));
		String station = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1")));
		send(analyst, HttpMethod.PATCH, "/api/v1/equipment/" + station + "/status",
				"{\"version\":0,\"status\":\"DELETED\"}");

		mvc.perform(get("/api/v1/inventory/summary").cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalEquipment").value(2))
				.andExpect(jsonPath("$.deletedEquipment").value(1))
				.andExpect(jsonPath("$.byType.CHARGING_POINT").value(2))
				.andExpect(jsonPath("$.byType.CHARGING_STATION").value(0))
				.andExpect(jsonPath("$.byNetwork.KIA").value(1))
				.andExpect(jsonPath("$.byNetwork.CONECTZ").value(0))
				.andExpect(jsonPath("$.pointsByUsageStatus.FREE").value(2))
				.andExpect(jsonPath("$.activeConnectorsByType.TYPE2").value(2));
	}

	@Test
	void pdfAndExcelReportsAreDownloadedWithoutRestrictedDataAndAudited() throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "UID-SECRETO"));
		send(analyst, HttpMethod.POST, "/api/v1/equipment", station("EST-1"));

		MvcResult pdf = mvc.perform(get("/api/v1/inventory/report").cookie(manager).param("format", "PDF"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", startsWith("attachment;")))
				.andReturn();
		assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

		MvcResult xlsx = mvc.perform(get("/api/v1/inventory/report").cookie(manager).param("format", "XLSX")
				.param("network", "KIA"))
				.andExpect(status().isOk())
				.andReturn();
		try (XSSFWorkbook workbook = new XSSFWorkbook(
				new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
			assertThat(workbook.getSheet("Resumen")).isNotNull();
			var detail = workbook.getSheet("Equipos");
			assertThat(detail.getLastRowNum()).isEqualTo(5);
			assertThat(detail.getRow(5).getCell(2).getStringCellValue()).isEqualTo("KIA-001");
			workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> assertThat(cell.toString())
					.doesNotContain("UID-SECRETO"))));
		}
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'INVENTORY_REPORT_GENERATED'",
				Long.class)).isEqualTo(2);
	}

	// ---- regresiones de la revision QA del 26-oct-2026

	@Test
	void aNullScheduleEntryIsRejectedAsInvalidInput() throws Exception {
		// QA-08: un elemento nulo en la lista llegaba al dominio y respondia 500.
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "U1")));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule",
				"{\"version\":0,\"mode\":\"WEEKLY\",\"intervals\":[null],\"exceptions\":[null]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void editsAreAuditedWithPreviousAndNewValuesButNeverRestrictedOnes() throws Exception {
		// QA-09: la auditoria conserva que cambio; UID/PUK y URL OCPP solo como hecho, sin su valor.
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "U1")));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, """
				{"version":0,"brand":"WALLBOX","name":"Punto renombrado","serial":"KIA-001","assignedCode":"123456",
				 "uidPuk":"UID-NUEVO","usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(CCS2)).andExpect(status().isOk());
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule", """
				{"version":1,"mode":"WEEKLY","intervals":[{"day":"MONDAY","opensAt":"08:00","closesAt":"18:00"}],
				 "exceptions":[{"date":"2026-12-25","closed":true}]}
				""").andExpect(status().isOk());

		String edit = jdbc.queryForObject("select details::text from audit_event where entity_id = ?::uuid "
				+ "and action = 'EQUIPMENT_UPDATED'", String.class, id);
		assertThat(edit).contains("\"nameFrom\": \"Punto KIA-001\"", "\"nameTo\": \"Punto renombrado\"",
				"\"connectorsFrom\": \"TYPE2\"", "\"connectorsTo\": \"CCS2\"", "\"uidPukChanged\": \"true\"")
				.doesNotContain("U1", "UID-NUEVO", "serialFrom");
		String schedule = jdbc.queryForObject("select details::text from audit_event where entity_id = ?::uuid "
				+ "and action = 'EQUIPMENT_SCHEDULE_DEFINED'", String.class, id);
		assertThat(schedule).contains("\"modeFrom\": \"NOT_DEFINED\"", "\"intervalsTo\": \"MONDAY 08:00-18:00\"",
				"\"exceptionsTo\": \"2026-12-25 cerrado\"");

		// Cambiar solo la nota de una excepcion tambien deja su valor anterior y el nuevo.
		String noted = """
				{"version":%d,"mode":"WEEKLY","intervals":[{"day":"MONDAY","opensAt":"08:00","closesAt":"18:00"}],
				 "exceptions":[{"date":"2026-12-25","closed":true,"note":"%s"}]}
				""";
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule", noted.formatted(2, "Navidad"))
				.andExpect(status().isOk());
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule", noted.formatted(3, "Mantenimiento"))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select details::text from audit_event where entity_id = ?::uuid "
				+ "and action = 'EQUIPMENT_SCHEDULE_DEFINED' order by occurred_at desc limit 1", String.class, id))
				.contains("\"exceptionsFrom\": \"2026-12-25 cerrado (Navidad)\"",
						"\"exceptionsTo\": \"2026-12-25 cerrado (Mantenimiento)\"");
	}

	@Test
	void anIntegrityViolationNeverWritesRestrictedValuesToTheLog() throws Exception {
		// QA-10: dos altas simultaneas con el mismo UID/PUK; la segunda la rechaza PostgreSQL.
		send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-001", "123456", "PUK-SECRETO"))
				.andExpect(status().isCreated());
		doReturn(List.of()).when(equipmentRepository).findHoldersOf(anyString(), anyString(), anyString());
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		root.addAppender(appender);
		try {
			send(analyst, HttpMethod.POST, "/api/v1/equipment", kiaPoint("KIA-002", "654321", "PUK-SECRETO"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
		}
		finally {
			root.detachAppender(appender);
		}
		String logged = appender.list.stream().map(event -> event.getFormattedMessage()
				+ (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
				.collect(Collectors.joining("\n"));
		assertThat(logged).contains("Violacion de integridad de datos").doesNotContain("PUK-SECRETO");
	}

	@Test
	void freeKiaAbsentCodesAreReadableAuditedAndExportable() throws Exception {
		for (String value : List.of("null", "\"\"", "\"   \"")) {
			String suffix = Integer.toString(value.length());
			String json = kiaPoint("FREE-" + suffix, "123456", "UID-" + suffix)
					.replace("\"assignedCode\":\"123456\"", "\"assignedCode\":" + value);
			String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", json));
			mvc.perform(get("/api/v1/equipment/" + id).cookie(analyst))
					.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").isEmpty());
			assertThat(jdbc.queryForObject("select assigned_code from asset_equipment where id=?::uuid",
					String.class, id)).isNull();
			assertThat(auditActions(id)).contains("EQUIPMENT_REGISTERED");
			assertThat(jdbc.queryForObject("select details::text from audit_event where entity_id=?::uuid",
					String.class, id)).doesNotContain("assignedCode");
		}
		String omitted = kiaPoint("FREE-OMITTED", "123456", "UID-OMITTED")
				.replace("\"assignedCode\":\"123456\",", "");
		createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment", omitted));
		mvc.perform(get("/api/v1/equipment").cookie(manager))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].assignedCode").isEmpty());
		for (String format : List.of("PDF", "XLSX")) {
			byte[] bytes = mvc.perform(get("/api/v1/inventory/report").cookie(manager).param("format", format))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
			assertThat(bytes).isNotEmpty();
			if (format.equals("XLSX")) {
				try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
					assertThat(workbook.getSheet("Equipos").getRow(5).getCell(1).getStringCellValue()).isEmpty();
				}
			}
		}
	}

	@Test
	void directCreateRequiresCodeForBlockedAndDisconnectedAndKeepsPermissions() throws Exception {
		for (String usage : List.of("BLOCKED", "DISCONNECTED")) {
			String json = kiaPoint("REQ-" + usage, "", "UID-" + usage).replace("FREE", usage);
			send(analyst, HttpMethod.POST, "/api/v1/equipment", json)
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("obligatorio")));
		}
		send(manager, HttpMethod.POST, "/api/v1/equipment", kiaPoint("NO", "", "NO"))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("select count(*) from asset_equipment", Long.class)).isZero();
	}

	@Test
	void usageOnlyEditsPreserveCodeAndHistoryForEveryAbsentRepresentation() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment",
				kiaPoint("HISTORY", "654321", "U1").replace("FREE", "BLOCKED")
						.replace("\"uidPuk\"", "\"location\":\"Quito\",\"mapsUrl\":\"https://maps.example.test/point\","
								+ "\"ocppUrl\":\"wss://ocpp.example.test\",\"uidPuk\"")));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id + "/schedule",
				"{\"version\":0,\"mode\":\"ALWAYS_OPEN\",\"intervals\":[],\"exceptions\":[]}").andExpect(status().isOk());
		send(analyst, HttpMethod.POST, "/api/v1/equipment/" + id + "/external-identities",
				"{\"platform\":\"WALLBOX\",\"externalId\":\"HISTORY-WB\"}")
				.andExpect(status().isCreated());
		String before = retainedBusinessSnapshot(id);
		String connectors = jdbc.queryForObject("select jsonb_agg(to_jsonb(c))::text from asset_connector c "
				+ "where equipment_id=?::uuid", String.class, id);
		String identities = jdbc.queryForObject("select jsonb_agg(to_jsonb(i))::text from asset_external_identity i "
				+ "where equipment_id=?::uuid", String.class, id);
		int version = 1;
		for (String fragment : List.of("", "\"assignedCode\":null,", "\"assignedCode\":\"\",",
				"\"assignedCode\":\"   \",")) {
			for (String usage : List.of("FREE", "BLOCKED")) {
				send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id,
						kiaUpdate("HISTORY", fragment + "\"location\":\"Quito\","
								+ "\"mapsUrl\":\"https://maps.example.test/point\","
								+ "\"ocppUrl\":\"wss://ocpp.example.test\",", usage, version++))
						.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").value("654321"))
						.andExpect(jsonPath("$.usageStatus").value(usage));
				assertThat(retainedBusinessSnapshot(id)).isEqualTo(before);
			}
		}
		assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(c))::text from asset_connector c "
				+ "where equipment_id=?::uuid", String.class, id)).isEqualTo(connectors);
		assertThat(jdbc.queryForObject("select jsonb_agg(to_jsonb(i))::text from asset_external_identity i "
				+ "where equipment_id=?::uuid", String.class, id)).isEqualTo(identities);
		assertThat(jdbc.queryForList("select details::text from audit_event where entity_id=?::uuid "
				+ "and action='EQUIPMENT_UPDATED'", String.class, id))
				.hasSize(8).allSatisfy(details -> assertThat(details).contains("usageStatusFrom", "usageStatusTo")
						.doesNotContain("assignedCodeFrom", "assignedCodeTo"));
	}

	@Test
	void invalidTransitionsRollbackAndExplicitCodeCorrectionsRemainAuthorized() throws Exception {
		String id = createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment",
				kiaPoint("TRANSITION", "", "U1")));
		createdId(send(analyst, HttpMethod.POST, "/api/v1/equipment",
				kiaPoint("RESERVED", "765432", "U2")));
		String before = fullSnapshot(id);
		for (String usage : List.of("BLOCKED", "DISCONNECTED")) {
			send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, kiaUpdate("TRANSITION", "", usage, 0)
					.replace("Punto TRANSITION", "Partial change"))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("obligatorio")));
			assertThat(fullSnapshot(id)).isEqualTo(before);
		}
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id, kiaUpdate("TRANSITION", "", "FREE", 0)
				.replace("Punto TRANSITION", "Renombrado sin codigo"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").isEmpty())
				.andExpect(jsonPath("$.name").value("Renombrado sin codigo"));
		// Use the persisted version for each subsequent edit.
		int version = jdbc.queryForObject("select version from asset_equipment where id=?::uuid", Integer.class, id);
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id,
				kiaUpdate("TRANSITION", "\"assignedCode\":\"654321\",", "BLOCKED", version))
				.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").value("654321"));
		version = jdbc.queryForObject("select version from asset_equipment where id=?::uuid", Integer.class, id);
		before = fullSnapshot(id);
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id,
				kiaUpdate("TRANSITION", "\"assignedCode\":\"765432\",", "BLOCKED", version))
				.andExpect(status().isConflict());
		assertThat(fullSnapshot(id)).isEqualTo(before);
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id,
				kiaUpdate("TRANSITION", "\"assignedCode\":\"876543\",", "BLOCKED", version))
				.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").value("876543"));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + id,
				kiaUpdate("TRANSITION", "", "FREE", version)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		assertThat(jdbc.queryForList("select details::text from audit_event where entity_id=?::uuid "
				+ "and action='EQUIPMENT_UPDATED'", String.class, id))
				.anySatisfy(details -> assertThat(details).contains("assignedCodeFrom", "654321", "assignedCodeTo", "876543"));
	}

	private String fullSnapshot(String id) {
		return jdbc.queryForObject("select to_jsonb(e)::text from asset_equipment e where id=?::uuid", String.class, id);
	}

	private String retainedBusinessSnapshot(String id) {
		return jdbc.queryForObject("select (to_jsonb(e) - 'usage_status' - 'updated_at' - 'updated_by' - 'version')::text "
				+ "from asset_equipment e where id=?::uuid", String.class, id);
	}

	private static String kiaUpdate(String serial, String codeFragment, String usage, int version) {
		return """
				{"version":%d,"brand":"WALLBOX","name":"Punto %s","serial":"%s",
				 %s"uidPuk":"U1","usageStatus":"%s","connectorTypeIds":["%s"]}
				""".formatted(version, serial, serial, codeFragment, usage, TYPE2);
	}

	@Test
	void simultaneousCreatesUseDatabaseUniquenessButTwoAbsentCodesCanCoexist() throws Exception {
		var checked = new java.util.concurrent.CountDownLatch(2);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			checked.countDown();
			assertThat(checked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			return result;
		}).when(equipmentRepository).findHoldersOf(anyString(), eq("654321"), anyString());
		try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			var first = executor.submit(() -> send(analyst,HttpMethod.POST,"/api/v1/equipment",
					kiaPoint("RACE-A","654321","UID-RACE-A")).andReturn());
			var second = executor.submit(() -> send(analyst,HttpMethod.POST,"/api/v1/equipment",
					kiaPoint("RACE-B","654321","UID-RACE-B")).andReturn());
			var results = List.of(first.get(30,java.util.concurrent.TimeUnit.SECONDS),
					second.get(30,java.util.concurrent.TimeUnit.SECONDS));
			assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(201,409);
			for (var result : results) {
				if (result.getResponse().getStatus() == 409) {
					assertThat(result.getResponse().getContentAsString()).contains("DATA_INTEGRITY_VIOLATION")
							.doesNotContain("UID-RACE", "insert into", "SQLException");
				}
			}
			assertThat(jdbc.queryForObject("select count(*) from asset_equipment",Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from audit_event where action='EQUIPMENT_REGISTERED'",Integer.class)).isEqualTo(1);
			var freeA = executor.submit(() -> send(analyst,HttpMethod.POST,"/api/v1/equipment",
					kiaPoint("NULL-A","","NULL-U-A")).andReturn());
			var freeB = executor.submit(() -> send(analyst,HttpMethod.POST,"/api/v1/equipment",
					kiaPoint("NULL-B","","NULL-U-B")).andReturn());
			assertThat(freeA.get(30,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
			assertThat(freeB.get(30,java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
			assertThat(jdbc.queryForObject("select count(*) from asset_equipment where assigned_code is null",Integer.class)).isEqualTo(2);
		}
	}

	// ---- helpers

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

	private List<String> auditActions(String equipmentId) {
		return jdbc.queryForList("select action from audit_event where entity_type = 'EQUIPMENT' "
				+ "and entity_id = ?::uuid order by occurred_at", String.class, equipmentId);
	}

	private static String kiaPoint(String serial, String code, String uidPuk) {
		return """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto %s","serial":"%s",
				 "assignedCode":"%s","uidPuk":"%s","usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(serial, serial, code, uidPuk, TYPE2);
	}

	private static String jacPoint(String serial, String uidPuk) {
		return """
				{"network":"JAC","type":"CHARGING_POINT","brand":"BENNY","name":"Punto %s","serial":"%s",
				 %s"usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(serial, serial, uidPuk == null ? "" : "\"uidPuk\":\"" + uidPuk + "\",", TYPE2);
	}

	private static String station(String serial) {
		return """
				{"network":"JAC","type":"CHARGING_STATION","brand":"BENNY","name":"Electrolinera %s","serial":"%s",
				 "location":"Quito","connectorTypeIds":["%s","%s","%s"]}
				""".formatted(serial, serial, CCS2, TYPE2, CHADEMO);
	}
}
