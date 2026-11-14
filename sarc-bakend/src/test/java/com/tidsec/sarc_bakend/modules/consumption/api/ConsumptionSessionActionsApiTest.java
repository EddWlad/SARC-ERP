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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import jakarta.servlet.http.Cookie;

/** Feature 009, US3 (T023 y T024): ver, editar y dar de baja una sesion, y la reimportacion que las respeta. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumptionSessionActionsApiTest {

	private static final String PASSWORD = "clave-de-prueba-123";
	private static final String TYPE2 = "00000000-0000-0000-0000-000000000c01";
	private static final String WALLBOX_HEADER = "Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n";
	private static final String SESSIONS = "/api/v1/energy/sessions/";

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
		jdbc.execute("TRUNCATE spring_session, spring_session_attributes, audit_event, auth_user_role, auth_user,"
				+ " energy_import_row, energy_session_revision, energy_session, energy_import_batch,"
				+ " asset_external_identity, asset_schedule_interval, asset_schedule_exception, asset_connector,"
				+ " asset_equipment, report_issued CASCADE");
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

	@Test
	void anEditorEditsWithAReasonAndTheOfficialFiguresAndHistoryFollow() throws Exception {
		importWallbox("S-0", "5,00");
		Map<String, Object> session = importWallbox("S-1", "8,00");
		String id = (String) session.get("id");
		assertOfficial(2, 13.0);

		// Escenario T016/T033: de 8 a 9 kWh con la accion Editar; el corte posterior pasa de 13 a 14 kWh.
		Map<String, Object> edited = read(patch(id, """
				{"version":%s,"reason":"Lectura corregida por el proveedor","energyKwh":9}""".formatted(session.get("version")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.energyKwh").value(9.0))
				.andExpect(jsonPath("$.revision").value(2))
				.andExpect(jsonPath("$.startedAt").value("2026-10-21T13:00:00Z"))
				.andExpect(jsonPath("$.state").value("VALIDATED")));
		assertOfficial(2, 14.0);
		mvc.perform(get(SESSIONS + id).cookie(analyst))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.session.energyKwh").value(9.0))
				.andExpect(jsonPath("$.revisions[0].kind").value("EDIT"))
				.andExpect(jsonPath("$.revisions[0].revision").value(1))
				.andExpect(jsonPath("$.revisions[0].energyKwh").value(8.0))
				.andExpect(jsonPath("$.revisions[0].reason").value("Lectura corregida por el proveedor"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'ENERGY_SESSION_EDITED'",
				Long.class)).isEqualTo(1);

		// Una version vieja no pisa la edicion; sin motivo no se acepta.
		patch(id, """
				{"version":%s,"reason":"Otro cambio","energyKwh":10}""".formatted(session.get("version")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		patch(id, """
				{"version":%s,"reason":"  ","energyKwh":10}""".formatted(edited.get("version")))
				.andExpect(status().isBadRequest());
		patch(id, """
				{"version":%s,"reason":"Sin cambios","energyKwh":9.0004}""".formatted(edited.get("version")))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SESSION_UNCHANGED"));

		// Energia explicitamente nula: desconocida, no 0 kWh.
		Map<String, Object> unknown = read(patch(id, """
				{"version":%s,"reason":"El proveedor anulo la lectura","energyKwh":null}""".formatted(edited.get("version")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.energyKwh").doesNotExist()));
		mvc.perform(summary().cookie(analyst))
				.andExpect(jsonPath("$.official.sessions").value(2))
				.andExpect(jsonPath("$.official.energyKwh").value(5.0))
				.andExpect(jsonPath("$.official.sessionsWithoutEnergy").value(1));

		// El inicio trae su desplazamiento, que pasa a ser el de origen y fija el dia de conteo.
		patch(id, """
				{"version":%s,"reason":"Inicio real segun bitacora","startedAt":"2026-10-22T00:30:00Z","energyKwh":5}"""
				.formatted(unknown.get("version")))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_SESSION"));
		// El fin anterior quedaria antes del nuevo inicio: se mueven juntos, cada uno con su desplazamiento.
		Map<String, Object> moved = read(patch(id, """
				{"version":%s,"reason":"Inicio real segun bitacora","startedAt":"2026-10-22T00:30:00Z",
				 "endedAt":"2026-10-21T20:10:00-05:00","energyKwh":5}"""
				.formatted(unknown.get("version")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sourceEndUtcOffsetMinutes").value(-300))
				.andExpect(jsonPath("$.sourceUtcOffsetMinutes").value(0))
				.andExpect(jsonPath("$.startDate").value("2026-10-22")));
		assertOfficial(1, 5.0);

		// Sobre el umbral queda por revisar y sale de las cifras oficiales; sin equipo, pendiente de vincular.
		Map<String, Object> high = read(patch(id, """
				{"version":%s,"reason":"Lectura acumulada","energyKwh":2000}""".formatted(moved.get("version")))
				.andExpect(jsonPath("$.state").value("ENERGY_REVIEW")));
		patch(id, """
				{"version":%s,"reason":"Equipo mal asignado","equipmentId":null}""".formatted(high.get("version")))
				.andExpect(jsonPath("$.state").value("PENDING_LINK"))
				.andExpect(jsonPath("$.equipment").doesNotExist());
		mvc.perform(get(SESSIONS + id).cookie(analyst)).andExpect(jsonPath("$.revisions.length()").value(5));
	}

	@Test
	void onlyEditorsMutateAndManagersOnlyReadValidatedActiveSessions() throws Exception {
		Map<String, Object> session = importWallbox("S-2", "8,00");
		String id = (String) session.get("id");

		patch(manager, id, """
				{"version":%s,"reason":"Intento","energyKwh":9}""".formatted(session.get("version")))
				.andExpect(status().isForbidden());
		send(manager, HttpMethod.POST, SESSIONS + id + "/deletion", """
				{"version":%s,"reason":"Intento"}""".formatted(session.get("version")))
				.andExpect(status().isForbidden());
		mvc.perform(get(SESSIONS + id).cookie(manager))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.session.id").value(id))
				.andExpect(jsonPath("$.revisions.length()").value(0))
				.andExpect(jsonPath("$.deletionReason").doesNotExist());
		mvc.perform(get(SESSIONS + "00000000-0000-0000-0000-00000000dead").cookie(analyst))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/energy/sessions").param("deleted", "true").cookie(manager))
				.andExpect(status().isForbidden());
		patch(id, """
				{"version":%s,"reason":"Equipo inexistente","equipmentId":"00000000-0000-0000-0000-00000000beef"}"""
				.formatted(session.get("version")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"));
	}

	@Test
	void aDeletionLeavesTheFiguresKeepsTheRecordAndIsIdempotent() throws Exception {
		Map<String, Object> session = importWallbox("S-3", "8,00");
		String id = (String) session.get("id");
		String body = """
				{"version":%s,"reason":"Sesion duplicada en el proveedor"}""".formatted(session.get("version"));

		Map<String, Object> deleted = read(send(analyst, HttpMethod.POST, SESSIONS + id + "/deletion", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deletedAt").isNotEmpty()));
		send(analyst, HttpMethod.POST, SESSIONS + id + "/deletion", body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(deleted.get("version")));

		assertOfficial(0, 0.0);
		mvc.perform(get("/api/v1/energy/sessions").cookie(analyst)).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/v1/energy/sessions").param("deleted", "true").cookie(analyst))
				.andExpect(jsonPath("$.content[0].id").value(id));
		mvc.perform(get(SESSIONS + id).cookie(analyst))
				.andExpect(jsonPath("$.deletionReason").value("Sesion duplicada en el proveedor"))
				.andExpect(jsonPath("$.revisions.length()").value(1))
				.andExpect(jsonPath("$.revisions[0].kind").value("DELETION"))
				.andExpect(jsonPath("$.revisions[0].state").value("VALIDATED"));
		mvc.perform(get(SESSIONS + id).cookie(manager)).andExpect(status().isNotFound());

		// Una baja no admite cambios por las demas operaciones.
		send(analyst, HttpMethod.PATCH, SESSIONS + id + "/test", """
				{"version":%s,"test":true}""".formatted(deleted.get("version")))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SESSION_DELETED"));
		patch(id, """
				{"version":%s,"reason":"Reactivar","energyKwh":9}""".formatted(deleted.get("version")))
				.andExpect(jsonPath("$.code").value("SESSION_DELETED"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'ENERGY_SESSION_DELETED'",
				Long.class)).isEqualTo(1);
	}

	@Test
	void aReimportNeitherReactivatesADeletionNorOverwritesAnEditMadeAfterThePreview() throws Exception {
		Map<String, Object> deleted = importWallbox("S-4", "8,00");
		send(analyst, HttpMethod.POST, SESSIONS + deleted.get("id") + "/deletion", """
				{"version":%s,"reason":"Carga de prueba del tecnico"}""".formatted(deleted.get("version")))
				.andExpect(status().isOk());
		String again = upload(wallbox("S-4", "8,00"));
		assertThat(batch(again)).containsEntry("deletedRows", 1);
		assertThat(rowField(again, "S-4", "classification")).isEqualTo("DELETED");
		confirm(again);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM energy_session WHERE deleted_at IS NULL", Long.class))
				.isZero();

		// Lote preparado antes de una edicion: su decision se tomo sobre otra version y no se aplica.
		Map<String, Object> session = importWallbox("S-5", "8,00");
		String prepared = upload(wallbox("S-5", "10,00"));
		assertThat(rowField(prepared, "S-5", "classification")).isEqualTo("MODIFIED");
		send(analyst, HttpMethod.PUT, "/api/v1/energy/imports/" + prepared + "/rows/decision",
				"{\"rowIds\":[\"" + rowField(prepared, "S-5", "id") + "\"],\"approved\":true}")
				.andExpect(status().isOk());
		patch((String) session.get("id"), """
				{"version":%s,"reason":"Lectura confirmada","energyKwh":9}""".formatted(session.get("version")))
				.andExpect(status().isOk());
		confirm(prepared);
		assertThat(rowField(prepared, "S-5", "outcome")).isEqualTo("SKIPPED");
		assertOfficial(1, 9.0);
	}

	// ---- ayudantes

	private Map<String, Object> importWallbox(String sessionId, String energy) throws Exception {
		confirm(upload(wallbox(sessionId, energy)));
		return read(mvc.perform(get("/api/v1/energy/sessions").param("text", sessionId).cookie(analyst)), "$.content[0]");
	}

	private static String wallbox(String sessionId, String energy) {
		return WALLBOX_HEADER + "WB-1001\tPlaza\t" + sessionId + "\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t" + energy
				+ "\n";
	}

	private void assertOfficial(int sessions, double energy) throws Exception {
		ResultActions result = mvc.perform(summary().cookie(analyst)).andExpect(status().isOk())
				.andExpect(jsonPath("$.official.sessions").value(sessions));
		if (sessions > 0) {
			result.andExpect(jsonPath("$.official.energyKwh").value(energy));
		}
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder summary() {
		return get("/api/v1/energy/summary").param("from", "2026-10-21").param("to", "2026-10-22");
	}

	private ResultActions patch(String id, String json) throws Exception {
		return patch(analyst, id, json);
	}

	private ResultActions patch(Cookie who, String id, String json) throws Exception {
		return send(who, HttpMethod.PATCH, SESSIONS + id, json);
	}

	private static Map<String, Object> read(ResultActions result) throws Exception {
		return read(result, "$");
	}

	private static Map<String, Object> read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

	private String rowField(String batchId, String externalSessionId, String field) throws Exception {
		List<Object> values = JsonPath.read(mvc.perform(get("/api/v1/energy/imports/" + batchId + "/rows").cookie(analyst))
				.andReturn().getResponse().getContentAsString(),
				"$.content[?(@.externalSessionId == '" + externalSessionId + "')]." + field);
		return String.valueOf(values.getFirst());
	}

	private String upload(String content) throws Exception {
		String id = JsonPath.read(mvc.perform(multipart("/api/v1/energy/imports")
				.file(new MockMultipartFile("file", "wallbox.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)))
				.param("source", "WALLBOX").with(xsrf()).cookie(analyst))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		awaitStatus(id, "READY");
		return id;
	}

	private void confirm(String id) throws Exception {
		send(analyst, HttpMethod.POST, "/api/v1/energy/imports/" + id + "/confirm",
				"{\"version\":" + batch(id).get("version") + "}").andExpect(status().isAccepted());
		awaitStatus(id, "COMPLETED");
	}

	private Map<String, Object> batch(String id) throws Exception {
		return read(mvc.perform(get("/api/v1/energy/imports/" + id).cookie(analyst)).andExpect(status().isOk()));
	}

	private void awaitStatus(String id, String expected) throws Exception {
		long deadline = System.currentTimeMillis() + 20_000;
		Map<String, Object> current = batch(id);
		while (!expected.equals(current.get("status"))) {
			if (!"PARSING".equals(current.get("status")) && !"CONFIRMING".equals(current.get("status"))) {
				fail("El lote quedo en " + current.get("status") + ": " + current.get("failureMessage"));
			}
			if (System.currentTimeMillis() > deadline) {
				fail("El lote no llego a " + expected);
			}
			Thread.sleep(50);
			current = batch(id);
		}
	}

	private void seedUser(String email, Role role) {
		UserAccount user = UserAccount.create(new Email(email), "Usuario " + email, Set.of(role), encoder.encode(PASSWORD));
		user.changePassword(encoder.encode(PASSWORD));
		users.save(user);
	}

	private ResultActions send(Cookie session, HttpMethod method, String path, String json) throws Exception {
		return mvc.perform(request(method, path).with(xsrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private static String createdId(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}
}
