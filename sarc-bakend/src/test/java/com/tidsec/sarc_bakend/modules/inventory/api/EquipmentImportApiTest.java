package com.tidsec.sarc_bakend.modules.inventory.api;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;

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
import com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Carga masiva de equipos contra PostgreSQL: plantilla, vista previa, aprobacion y confirmacion. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentImportApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";
	private static final String TYPE2 = "00000000-0000-0000-0000-000000000c01";
	private static final String HEADER = String.join("\t",
			Arrays.stream(EquipmentSheetColumns.values()).map(EquipmentSheetColumns::header).toList()) + "\n";

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage fileStorage;

	private Cookie analyst;
	private Cookie manager;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user, "
				+ "asset_import_row, asset_import_batch, energy_import_row, energy_session_revision, energy_session, "
				+ "energy_import_batch, asset_external_identity, asset_schedule_interval, asset_schedule_exception, "
				+ "asset_connector, asset_equipment CASCADE");
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenReturn("sarc/imports/original");
		seedUser("analista@eos.com", Role.ANALISTA);
		seedUser("gerente@eos.com", Role.GERENTE);
		analyst = login(mvc, "analista@eos.com", PASSWORD);
		manager = login(mvc, "gerente@eos.com", PASSWORD);
	}

	@Test
	void templateHasExactlyTheColumnsTheReaderExpects() throws Exception {
		byte[] xlsx = mvc.perform(get("/api/v1/equipment/imports/template").cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"plantilla-carga-equipos.xlsx\""))
				.andReturn().getResponse().getContentAsByteArray();

		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			var instructions = new StringBuilder();
			workbook.getSheet("Instrucciones").forEach(row -> row.forEach(cell -> instructions.append(cell.toString()).append(" ")));
			assertThat(instructions.toString()).contains("Libre", "Bloqueado", "Desconectado");
			assertThat(EquipmentSheetColumns.ASSIGNED_CODE.hint()).contains("Libre", "Bloqueado", "Desconectado");
		}
		List<TabularRow> rows = new TabularReader().read(xlsx);
		assertThat(rows.getFirst().cells())
				.containsExactlyElementsOf(Arrays.stream(EquipmentSheetColumns.values()).map(EquipmentSheetColumns::header).toList());
		mvc.perform(get("/api/v1/equipment/imports/template").cookie(manager)).andExpect(status().isForbidden());
	}

	@Test
	void newEquipmentIsCreatedWithItsConnectorsAndVerifiedIdentities() throws Exception {
		String id = paste(HEADER
				+ row("KIA", "Punto de carga", "Wallbox", "Punto Quicentro", "WB-1001", "204518", "PUK-1", "Libre",
						"TYPE2", "", "", "", "", "")
				+ row("CONECTZ", "Electrolinera", "Benny", "Estacion 4", "CZ-4", "", "", "", "CCS2", "TYPE2", "",
						"Conectz", "ESTACION 4", "CP-4"));
		assertThat(batch(id)).containsEntry("newRows", 2).containsEntry("totalRows", 2);

		assertThat(confirm(id)).containsEntry("appliedRows", 2);

		mvc.perform(get("/api/v1/equipment").param("text", "CZ-4").cookie(manager))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].connectorTypeCodes[0]").value("CCS2"))
				.andExpect(jsonPath("$.content[0].connectorTypeCodes[1]").value("TYPE2"));
		String station = equipmentId("CZ-4");
		mvc.perform(get("/api/v1/equipment/" + station + "/external-identities").cookie(analyst))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.platform == 'ENERLINK')].scope").value("Conectz"))
				.andExpect(jsonPath("$[?(@.platform == 'ENERLINK')].verified").value(true))
				.andExpect(jsonPath("$[?(@.platform == 'OCPP')].externalId").value("CP-4"));
		assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'EQUIPMENT_REGISTERED' "
				+ "and actor_id is not null", Integer.class)).isEqualTo(2);
	}

	@Test
	void rowsThatCannotBeAppliedAreClassifiedWithTheirReasonAndNeverWritten() throws Exception {
		String kia = createKiaPoint("WB-1001", "204518", "PUK-1", "Quicentro");
		String deleted = createKiaPoint("WB-9", "300000", "PUK-9", null);
		send(analyst, HttpMethod.PATCH, "/api/v1/equipment/" + deleted + "/status",
				"{\"version\":0,\"status\":\"DELETED\"}").andExpect(status().isOk());

		String id = paste(HEADER
				+ row("TESLA", "Punto de carga", "Wallbox", "X", "N-1", "", "", "", "TYPE2", "", "", "", "", "")
				+ row("KIA", "Punto de carga", "Wallbox", "Sin codigo", "N-2", "", "PUK-2", "Bloqueado", "TYPE2", "", "",
						"", "", "")
				+ row("JAC", "Punto de carga", "Benny", "Nuevo", "N-3", "", "PUK-3", "Libre", "TYPE2", "", "", "", "",
						"")
				+ row("JAC", "Punto de carga", "Benny", "Repetido", "n-3", "", "PUK-4", "Libre", "TYPE2", "", "", "",
						"", "")
				+ row("JAC", "Punto de carga", "Benny", "Ajeno", "N-5", "", "puk-1", "Libre", "TYPE2", "", "", "", "",
						"")
				+ row("KIA", "Punto de carga", "Wallbox", "Eliminado", "WB-9", "300000", "PUK-9", "Libre", "TYPE2", "",
						"", "", "", "")
				+ row("CONECTZ", "Electrolinera", "Benny", "Otro tipo", "WB-1001", "", "", "", "CCS2", "", "", "", "",
						""));

		assertThat(batch(id)).containsEntry("newRows", 1).containsEntry("invalidRows", 2)
				.containsEntry("duplicateRows", 1).containsEntry("deletedExistsRows", 1)
				.containsEntry("conflictRows", 2);
		assertThat(issueCodes(id, 3)).containsExactly("INVALID_ASSIGNED_CODE");
		assertThat(issueCodes(id, 5)).containsExactly("DUPLICATED_IN_BATCH");
		assertThat(issueCodes(id, 6)).containsExactly("EQUIPMENT_IDENTIFIER_IN_USE");
		assertThat(issueCodes(id, 7)).containsExactly("EQUIPMENT_DELETED_EXISTS");
		assertThat(issueCodes(id, 8)).containsExactly("CLASSIFICATION_IMMUTABLE");

		assertThat(confirm(id)).containsEntry("appliedRows", 1);
		assertThat(jdbc.queryForObject("select count(*) from asset_equipment", Integer.class)).isEqualTo(3);
		mvc.perform(get("/api/v1/equipment/" + kia).cookie(analyst)).andExpect(jsonPath("$.name").value("Punto WB-1001"));
	}

	@Test
	void existingEquipmentIsOnlyUpdatedWithApprovalAndEmptyCellsKeepItsData() throws Exception {
		String kia = createKiaPoint("WB-1001", "204518", "PUK-1", "Quicentro");
		String update = HEADER + row("KIA", "Punto de carga", "", "Punto renovado", "wb-1001", "", "", "Bloqueado",
				"", "", "", "", "", "OCPP-77");

		String first = paste(update);
		assertThat(batch(first)).containsEntry("modifiedRows", 1);
		assertThat(issues(first, 2).getFirst()).contains("nombre").contains("estado de uso")
				.contains("nueva identidad OCPP OCPP-77").doesNotContain("ubicacion");
		assertThat(confirm(first)).containsEntry("appliedRows", 0);
		assertThat(rowField(first, 2, "outcome")).isEqualTo("SKIPPED");
		mvc.perform(get("/api/v1/equipment/" + kia).cookie(analyst)).andExpect(jsonPath("$.name").value("Punto WB-1001"));

		String second = paste(update);
		approve(second, rowField(second, 2, "id"));
		assertThat(confirm(second)).containsEntry("appliedRows", 1);
		mvc.perform(get("/api/v1/equipment/" + kia).cookie(analyst))
				.andExpect(jsonPath("$.name").value("Punto renovado"))
				.andExpect(jsonPath("$.usageStatus").value("BLOCKED"))
				.andExpect(jsonPath("$.location").value("Quicentro"))
				.andExpect(jsonPath("$.uidPuk").value("PUK-1"))
				.andExpect(jsonPath("$.connectors[0].connectorTypeCode").value("TYPE2"));

		String third = paste(update);
		assertThat(batch(third)).containsEntry("identicalRows", 1).containsEntry("modifiedRows", 0);
	}

	@Test
	void anUpdateIsSkippedIfTheEquipmentChangedAfterThePreview() throws Exception {
		String kia = createKiaPoint("WB-1001", "204518", "PUK-1", null);
		String id = paste(HEADER + row("KIA", "Punto de carga", "", "Desde plantilla", "WB-1001", "", "", "", "", "",
				"", "", "", ""));
		approve(id, rowField(id, 2, "id"));
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/" + kia, """
				{"version":0,"brand":"WALLBOX","name":"Editado a mano","serial":"WB-1001","assignedCode":"204518",
				 "uidPuk":"PUK-1","usageStatus":"FREE","connectorTypeIds":["%s"]}
				""".formatted(TYPE2)).andExpect(status().isOk());

		assertThat(confirm(id)).containsEntry("appliedRows", 0);

		assertThat(rowField(id, 2, "outcome")).isEqualTo("SKIPPED");
		assertThat(rowField(id, 2, "outcomeDetail")).contains("se modifico despues de la vista previa");
		mvc.perform(get("/api/v1/equipment/" + kia).cookie(analyst)).andExpect(jsonPath("$.name").value("Editado a mano"));
	}

	@Test
	void aFileThatIsNotTheTemplateFailsWithTheMissingColumns() throws Exception {
		String id = JsonPath.read(mvc.perform(multipart("/api/v1/equipment/imports")
				.file(new MockMultipartFile("file", "otro.csv", "text/csv",
						"Serie;Nombre\nX;Y\n".getBytes(StandardCharsets.UTF_8)))
				.with(xsrf()).cookie(analyst))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString(), "$.id");

		Map<String, Object> failed = awaitStatus(id, "FAILED");

		assertThat(failed).containsEntry("failureCode", "IMPORT_COLUMNS_MISSING");
		assertThat((String) failed.get("failureMessage")).contains("Red").contains("Conector 1");
	}

	@Test
	void onlyEditorsImport() throws Exception {
		send(manager, HttpMethod.POST, "/api/v1/equipment/imports/paste", "{\"text\":\"x\"}")
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/equipment/imports").cookie(manager)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void freeCodeMatrixWorksInPasteAndXlsxAndRetryCreatesNoDuplicates(boolean file) throws Exception {
		createKiaPoint("RESERVED", "654321", "RESERVED-U", null);
		String text = HEADER
				+ row("KIA","Punto de carga","Wallbox","Free A","FREE-A","","U-A","Libre","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Free B","FREE-B","   ","U-B","Libre","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Blocked","REQ-B","","U-C","Bloqueado","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Disconnected","REQ-D","","U-D","Desconectado","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Invalid","INVALID","012345","U-E","Libre","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Reserved","DUP","654321","U-F","Libre","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Valid","VALID","987654","U-G","Libre","TYPE2","","","","","")
				+ row("KIA","Punto de carga","Wallbox","Batch duplicate","BATCH-DUP","987654","U-H","Libre","TYPE2","","","","","");
		String id = importText(file, text);
		assertThat(batch(id)).containsEntry("newRows",3).containsEntry("invalidRows",3)
				.containsEntry("conflictRows",1).containsEntry("duplicateRows",1);
		assertThat(issues(id,4).getFirst()).contains("obligatorio");
		assertThat(issues(id,5).getFirst()).contains("obligatorio");
		assertThat(confirm(id)).containsEntry("appliedRows",3);
		assertThat(jdbc.queryForObject("select count(*) from asset_equipment where assigned_code is null", Integer.class))
				.isEqualTo(2);
		assertThat(jdbc.queryForList("select details::text from audit_event where action='EQUIPMENT_REGISTERED' "
				+ "and entity_id in (select id from asset_equipment where assigned_code is null)", String.class))
				.hasSize(2).allSatisfy(details -> assertThat(details).contains("importBatch").doesNotContain("assignedCode"));
		String retry = importText(file, text);
		assertThat(batch(retry)).containsEntry("newRows",0).containsEntry("identicalRows",3);
		assertThat(confirm(retry)).containsEntry("appliedRows",0);
		assertThat(jdbc.queryForObject("select count(*) from asset_equipment", Integer.class)).isEqualTo(4);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void importTransitionsPreserveCodesRequireApprovalAndPermitExplicitCorrection(boolean file) throws Exception {
		String kept = createKiaPoint("KEPT","654321","U1",null);
		createKiaPoint("RESERVED","765432","U2",null);
		String first = importText(file, HEADER + row("KIA","Punto de carga","","","KEPT","","","Bloqueado","","","","","",""));
		assertThat(batch(first)).containsEntry("modifiedRows",1);
		assertThat(confirm(first)).containsEntry("appliedRows",0);
		for (String usage : List.of("Bloqueado","Libre")) {
			String id = importText(file, HEADER + row("KIA","Punto de carga","","","KEPT","","",usage,"","","","","",""));
			approve(id,rowField(id,2,"id"));
			assertThat(confirm(id)).containsEntry("appliedRows",1);
			mvc.perform(get("/api/v1/equipment/"+kept).cookie(analyst))
					.andExpect(jsonPath("$.assignedCode").value("654321"));
		}
		String correction = importText(file, HEADER + row("KIA","Punto de carga","","","KEPT","876543","","Libre","","","","","",""));
		assertThat(batch(correction)).containsEntry("modifiedRows",1);
		approve(correction,rowField(correction,2,"id"));
		assertThat(confirm(correction)).containsEntry("appliedRows",1);
		mvc.perform(get("/api/v1/equipment/"+kept).cookie(analyst)).andExpect(jsonPath("$.assignedCode").value("876543"));
		String duplicate = importText(file, HEADER + row("KIA","Punto de carga","","","KEPT","765432","","Libre","","","","","",""));
		assertThat(batch(duplicate)).containsEntry("conflictRows",1);
		assertThat(confirm(duplicate)).containsEntry("appliedRows",0);
		String created = importText(file, HEADER + row("KIA","Punto de carga","Wallbox","Free","ABSENT","","U3","Libre","TYPE2","","","","",""));
		assertThat(confirm(created)).containsEntry("appliedRows",1);
		String absent = equipmentId("ABSENT");
		String rejected = importText(file, HEADER + row("KIA","Punto de carga","","","ABSENT","","","Bloqueado","","","","","",""));
		assertThat(batch(rejected)).containsEntry("invalidRows",1);
		assertThat(confirm(rejected)).containsEntry("appliedRows",0);
		mvc.perform(get("/api/v1/equipment/"+absent).cookie(analyst))
				.andExpect(jsonPath("$.usageStatus").value("FREE")).andExpect(jsonPath("$.assignedCode").isEmpty());
		String valid = importText(file, HEADER + row("KIA","Punto de carga","","","ABSENT","987654","","Bloqueado","","","","","",""));
		approve(valid,rowField(valid,2,"id"));
		assertThat(confirm(valid)).containsEntry("appliedRows",1);
		mvc.perform(get("/api/v1/equipment/"+absent).cookie(analyst))
				.andExpect(jsonPath("$.usageStatus").value("BLOCKED")).andExpect(jsonPath("$.assignedCode").value("987654"));
	}

	@Test
	void previewWithoutCodeIsRevalidatedWhenAnotherRequestCreatesTheEquipment() throws Exception {
		String text = HEADER + row("KIA","Punto de carga","Wallbox","Free","NEW-FREE","","U1","Libre","TYPE2","","","","","");
		String id = paste(text);
		String other = paste(text);
		assertThat(confirm(other)).containsEntry("appliedRows",1);
		assertThat(confirm(id)).containsEntry("appliedRows",0);
		assertThat(jdbc.queryForObject("select count(*) from asset_equipment",Integer.class)).isEqualTo(1);
	}

	private String importText(boolean file, String text) throws Exception {
		if (!file) return paste(text);
		byte[] bytes;
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("Equipos");
			int index = 0;
			for (String line : text.split("\n")) {
				var excelRow = sheet.createRow(index++);
				String[] cells = line.split("\t",-1);
				for (int column = 0; column < cells.length; column++) excelRow.createCell(column).setCellValue(cells[column]);
			}
			workbook.write(output);
			bytes = output.toByteArray();
		}
		String id = JsonPath.read(mvc.perform(multipart("/api/v1/equipment/imports")
				.file(new MockMultipartFile("file","equipos.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes))
				.with(xsrf()).cookie(analyst)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(),"$.id");
		awaitStatus(id,"READY");
		return id;
	}

	// ---- helpers

	/** Fila de la plantilla; ubicacion, enlaces y Wallbox ID quedan vacios. */
	private static String row(String network, String type, String brand, String name, String serial, String code,
			String uid, String usage, String connector1, String connector2, String connector3, String enerlinkScope,
			String enerlinkCharger, String ocppId) {
		return String.join("\t", network, type, brand, name, serial, code, uid, "", "", "", usage, connector1,
				connector2, connector3, enerlinkScope, enerlinkCharger, "", ocppId) + "\n";
	}

	private String paste(String text) throws Exception {
		String body = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\t", "\\t")
				.replace("\n", "\\n") + "\"}";
		String id = JsonPath.read(send(analyst, HttpMethod.POST, "/api/v1/equipment/imports/paste", body)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status").value("PARSING"))
				.andReturn().getResponse().getContentAsString(), "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	private Map<String, Object> confirm(String id) throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/equipment/imports/" + id + "/confirm",
				"{\"version\":" + batch(id).get("version") + "}")
				.andExpect(status().isAccepted());
		return awaitStatus(id, "COMPLETED");
	}

	private void approve(String batchId, String rowId) throws Exception {
		send(analyst, HttpMethod.PUT, "/api/v1/equipment/imports/" + batchId + "/rows/decision",
				"{\"rowIds\":[\"" + rowId + "\"],\"approved\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].approved").value(true));
	}

	private Map<String, Object> batch(String id) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/equipment/imports/" + id).cookie(analyst))
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

	private String rows(String batchId) throws Exception {
		return mvc.perform(get("/api/v1/equipment/imports/" + batchId + "/rows").cookie(analyst))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String rowField(String batchId, int rowNumber, String field) throws Exception {
		List<String> values = JsonPath.read(rows(batchId), "$.content[?(@.rowNumber == " + rowNumber + ")]." + field);
		return values.getFirst();
	}

	private List<String> issueCodes(String batchId, int rowNumber) throws Exception {
		return JsonPath.read(rows(batchId), "$.content[?(@.rowNumber == " + rowNumber + ")].issues[*].code");
	}

	private List<String> issues(String batchId, int rowNumber) throws Exception {
		return JsonPath.read(rows(batchId), "$.content[?(@.rowNumber == " + rowNumber + ")].issues[*].message");
	}

	private String createKiaPoint(String serial, String code, String uid, String location) throws Exception {
		String json = """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Punto %s","serial":"%s",
				 "assignedCode":"%s","uidPuk":"%s","usageStatus":"FREE","connectorTypeIds":["%s"]%s}
				""".formatted(serial, serial, code, uid, TYPE2,
				location == null ? "" : ",\"location\":\"" + location + "\"");
		return JsonPath.read(send(analyst, HttpMethod.POST, "/api/v1/equipment", json)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String equipmentId(String serial) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/equipment").param("text", serial).cookie(analyst))
				.andReturn().getResponse().getContentAsString(), "$.content[0].id");
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
}
