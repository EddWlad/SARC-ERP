package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.tidsec.sarc_bakend.support.Feature005Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Base de las pruebas de API de uptime: inventario sembrado, analista y gerente con sesion real. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class UptimeApiSupport {

	static final String START = "2026-10-30T14:00:00-05:00";
	static final String END = "2026-10-31T14:00:00-05:00";
	static final String PASSWORD = "Prueba-Uptime-2026";
	static final UUID JAC_1 = Feature005Fixtures.connectorId(Feature005Fixtures.JAC_STATION, 1);
	static final UUID JAC_2 = Feature005Fixtures.connectorId(Feature005Fixtures.JAC_STATION, 2);

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	Cookie analyst;
	Cookie manager;

	@BeforeEach
	void prepare() throws Exception {
		jdbc.execute("TRUNCATE upt_import_row, upt_measurement_exclusion, upt_measurement_revision, upt_measurement,"
				+ " upt_import_batch, upt_connector_alias, asset_equipment, auth_user, spring_session, audit_event CASCADE");
		Feature005Fixtures.seedInventory(jdbc);
		user("analista", Role.ANALISTA);
		user("gerente", Role.GERENTE);
		analyst = WebSessions.login(mvc, "analista@qa005.example.test", PASSWORD);
		manager = WebSessions.login(mvc, "gerente@qa005.example.test", PASSWORD);
	}

	private void user(String name, Role role) {
		String hash = encoder.encode(PASSWORD);
		UserAccount account = UserAccount.create(new Email(name + "@qa005.example.test"), name, Set.of(role), hash);
		account.changePassword(hash);
		users.save(account);
	}

	static String measurement(UUID connector, String available, String offline) {
		return """
				{"connectorId":"%s","source":"ENERLINK","periodStart":"%s","periodEnd":"%s","periodType":"ROLLING_24H",
				 "exactConfirmed":false,"availableMinutes":%s,"occupiedMinutes":0,"faultMinutes":0,
				 "offlineMinutes":%s,"origin":"MANUAL"}
				""".formatted(connector, START, END, available, offline);
	}

	static String correction(long version, String available, String offline) {
		return """
				{"version":%d,"periodType":"ROLLING_24H","exactConfirmed":false,"availableMinutes":%s,
				 "occupiedMinutes":0,"faultMinutes":0,"offlineMinutes":%s,"justification":"Dato corregido"}
				""".formatted(version, available, offline);
	}

	ResultActions send(Cookie session, String path, String json) throws Exception {
		return mvc.perform(post("/api/v1/uptime" + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Registra y devuelve id y version. */
	Map<String, Object> registered(UUID connector, String available, String offline) throws Exception {
		String body = send(analyst, "/measurements", measurement(connector, available, offline))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return Map.of("id", JsonPath.read(body, "$.id"), "version", ((Number) JsonPath.read(body, "$.version")).longValue());
	}

	long version(String id) {
		return jdbc.queryForObject("SELECT version FROM upt_measurement WHERE id = ?::uuid", Long.class, id);
	}

	void validate(String id) throws Exception {
		send(analyst, "/measurements/" + id + "/validate", "{\"version\":%d}".formatted(version(id)))
				.andExpect(status().isOk());
	}
}
