package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

/**
 * Base de las pruebas de API de intermitencias: puntos KIA sinteticos, un usuario con sesion real por cada
 * combinacion de roles de {@link Feature008Fixtures#ROLE_SETS} y ayudas para crear ventanas y comprobar errores.
 */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class IntermittencyApiSupport {

	static final String BASE = "/api/v1/intermittency";
	static final String DAY = "2026-10-15";

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

	private final Map<Set<Role>, Cookie> sessions = new HashMap<>();

	@BeforeEach
	void prepare() throws Exception {
		jdbc.execute("TRUNCATE itm_reviewed_day_event, itm_reviewed_day, itm_window_revision, itm_import_row,"
				+ " itm_window, itm_import_batch, asset_equipment, auth_user, spring_session, audit_event CASCADE");
		Feature008Fixtures.seedInventory(jdbc);
		sessions.clear();
		String hash = encoder.encode(Feature008Fixtures.PASSWORD);
		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			UserAccount account = UserAccount.create(new Email(Feature008Fixtures.emailFor(roles)), "Prueba", roles, hash);
			account.changePassword(hash);
			users.save(account);
			sessions.put(roles, WebSessions.login(mvc, Feature008Fixtures.emailFor(roles), Feature008Fixtures.PASSWORD));
		}
	}

	/** Sesion del usuario que tiene exactamente esos roles. */
	Cookie as(Role... roles) {
		Set<Role> wanted = EnumSet.copyOf(java.util.List.of(roles));
		Cookie session = sessions.get(wanted);
		if (session == null) {
			throw new IllegalArgumentException("No hay un usuario de prueba con los roles " + wanted);
		}
		return session;
	}

	Cookie analyst() {
		return as(Role.ANALISTA);
	}

	ResultActions post(Cookie session, String path, String json) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.post(BASE + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions put(Cookie session, String path) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.put(BASE + path).cookie(session).with(WebSessions.xsrf()));
	}

	ResultActions patchAs(Cookie session, String path, String json) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.patch(BASE + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	ResultActions get(Cookie session, String path) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.get(BASE + path).cookie(session));
	}

	/** Comprueba un problem+json con ese estado HTTP y ese codigo estable. */
	static ResultActions assertProblem(ResultActions result, int status, String code) throws Exception {
		return result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
	}

	// ----- Cuerpos -----

	/** Hora local de Guayaquil (-05:00) del dia de pruebas. */
	static String at(String time) {
		return at(DAY, time);
	}

	static String at(String day, String time) {
		return day + "T" + time + ":00-05:00";
	}

	/** Una ventana con fin; sin fin, abierta. */
	static String window(String start, String end) {
		return end == null ? "{\"startAt\":\"%s\"}".formatted(start)
				: "{\"startAt\":\"%s\",\"endAt\":\"%s\"}".formatted(start, end);
	}

	static String capture(UUID equipment, String... windows) {
		return "{\"equipmentId\":\"%s\",\"windows\":[%s]}".formatted(equipment, String.join(",", windows));
	}

	// ----- Datos directos -----

	/** Inserta una ventana con su primera revision, sin pasar por las reglas de captura. */
	UUID insertWindow(UUID equipment, String start, String end, String state) {
		UUID id = UUID.randomUUID();
		Timestamp from = Timestamp.from(OffsetDateTime.parse(start).toInstant());
		Timestamp to = end == null ? null : Timestamp.from(OffsetDateTime.parse(end).toInstant());
		Integer minutes = end == null ? null
				: (int) java.time.Duration.between(OffsetDateTime.parse(start), OffsetDateTime.parse(end)).toMinutes();
		String reasons = minutes != null && minutes >= 240 && !"CONFIRMED".equals(state) ? "[\"LONG_WINDOW\"]" : "[]";
		jdbc.update("""
				INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
				    review_reasons, source, current_revision, created_at, updated_at, version)
				VALUES (?, ?, 'QA008 Punto', ?, ?, ?, ?, ?::jsonb, 'MANUAL', 1, now(), now(), 0)
				""", id, equipment, from, to, minutes, state, reasons);
		jdbc.update("""
				INSERT INTO itm_window_revision (id, window_id, revision, start_at, end_at, duration_minutes, state,
				    review_reasons, change, actor_id, at)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?::jsonb, 'CREATED', ?, now())
				""", UUID.randomUUID(), id, from, to, minutes, state, reasons, UUID.randomUUID());
		return id;
	}

	int count(String where) {
		return jdbc.queryForObject("SELECT count(*) FROM itm_window WHERE " + where, Integer.class);
	}
}
