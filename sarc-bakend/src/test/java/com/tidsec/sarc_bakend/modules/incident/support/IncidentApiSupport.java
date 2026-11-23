package com.tidsec.sarc_bakend.modules.incident.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.tidsec.sarc_bakend.support.Feature010Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/**
 * Base de las pruebas de API de incidencias: equipos sinteticos, un usuario con sesion real por cada combinacion de
 * roles de {@link Feature010Fixtures#ROLE_SETS}, un segundo Analista, la numeracion de prueba configurada y ayudas
 * para llamar al API y comprobar errores. Las pruebas de US2 a US7 la usan sin depender de que otras historias esten
 * terminadas.
 */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IncidentApiSupport {

	protected static final String BASE = "/api/v1/incidents";

	@MockitoBean
	protected FileStorage fileStorage;

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PasswordEncoder encoder;

	private final Map<Set<Role>, Cookie> sessions = new HashMap<>();
	private final Map<Set<Role>, UUID> userIds = new HashMap<>();
	private Cookie secondAnalystSession;
	private UUID secondAnalystId;

	@BeforeEach
	protected void prepare() throws Exception {
		Feature010Fixtures.clearIncidents(jdbc);
		jdbc.execute("TRUNCATE asset_equipment, auth_user, spring_session, audit_event CASCADE");
		Feature010Fixtures.seedInventory(jdbc);
		Feature010Fixtures.configureNumbering(jdbc);
		sessions.clear();
		userIds.clear();
		String hash = encoder.encode(Feature010Fixtures.PASSWORD);
		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			String email = Feature010Fixtures.emailFor(roles);
			UserAccount account = createUser(email, "Prueba " + String.join("-", roles.stream().map(Role::name).sorted().toList()),
					roles, hash);
			userIds.put(roles, account.getId());
			sessions.put(roles, WebSessions.login(mvc, email, Feature010Fixtures.PASSWORD));
		}
		UserAccount second = createUser(Feature010Fixtures.SECOND_ANALYST_EMAIL, "Segundo Analista",
				Set.of(Role.ANALISTA), hash);
		secondAnalystId = second.getId();
		secondAnalystSession = WebSessions.login(mvc, Feature010Fixtures.SECOND_ANALYST_EMAIL,
				Feature010Fixtures.PASSWORD);
	}

	private UserAccount createUser(String email, String name, Set<Role> roles, String hash) {
		UserAccount account = UserAccount.create(new Email(email), name, roles, hash);
		account.changePassword(hash);
		return users.save(account);
	}

	/** Sesion del usuario que tiene exactamente esos roles. */
	protected Cookie as(Role... roles) {
		Set<Role> wanted = EnumSet.copyOf(List.of(roles));
		Cookie session = sessions.get(wanted);
		if (session == null) {
			throw new IllegalArgumentException("No hay un usuario de prueba con los roles " + wanted);
		}
		return session;
	}

	protected UUID userId(Role... roles) {
		UUID id = userIds.get(EnumSet.copyOf(List.of(roles)));
		if (id == null) {
			throw new IllegalArgumentException("No hay un usuario de prueba con esos roles");
		}
		return id;
	}

	protected Cookie admin() {
		return as(Role.ADMINISTRADOR);
	}

	protected Cookie analyst() {
		return as(Role.ANALISTA);
	}

	protected Cookie secondAnalyst() {
		return secondAnalystSession;
	}

	protected UUID secondAnalystId() {
		return secondAnalystId;
	}

	/** Los tres roles de solo consulta, sin combinaciones. */
	protected List<Cookie> readOnlyRoles() {
		return List.of(as(Role.GERENTE), as(Role.SUPERVISOR), as(Role.ASISTENTE));
	}

	// ----- Llamadas -----

	protected ResultActions get(Cookie session, String path) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.get(BASE + path).cookie(session));
	}

	protected ResultActions post(Cookie session, String path, String json) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.post(BASE + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions put(Cookie session, String path, String json) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.put(BASE + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected ResultActions patch(Cookie session, String path, String json) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.patch(BASE + path).cookie(session).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/** Comprueba un problem+json con ese estado HTTP y ese codigo estable. */
	protected static ResultActions assertProblem(ResultActions result, int status, String code) throws Exception {
		return result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
	}

	// ----- Datos directos -----

	/**
	 * Inserta un ticket de equipo ya abierto directamente en la base, para probar lo que depende de que existan
	 * tickets (como el bloqueo del numero inicial) sin pasar por la API de creacion.
	 */
	protected UUID insertEquipmentTicket(int number) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_ticket (id, ticket_number, client_request_id, state, element_class, equipment_id,
				    equipment_label_at_t0, network_at_t0, infrastructure_type_at_t0, incident_type, report_origin,
				    description, priority, current_responsible_id, current_responsible_name, duplicate_key,
				    last_activity_at, created_at, updated_at, version)
				VALUES (?, ?, ?, 'OPEN', 'EQUIPMENT', ?, 'Equipo de prueba', 'KIA', 'CHARGING_POINT', 'DISCONNECTION',
				    'PLATFORM', 'Descripcion de prueba', 'HIGH', ?, 'Analista', ?, ?, ?, ?, 0)
				""", id, number, UUID.randomUUID(), Feature010Fixtures.JAC_POINT, userId(Role.ANALISTA, Role.GERENTE),
				"EQ:" + Feature010Fixtures.JAC_POINT, now, now, now);
		return id;
	}

	// ----- Creacion de tickets -----

	/** Cuerpo minimo de un ticket de equipo: elemento, tipo y descripcion (FR-010). */
	protected static String equipmentBody(UUID equipment, String type, String description) {
		return equipmentBody(UUID.randomUUID(), equipment, List.of(), type, description, "");
	}

	/** {@code extra} se agrega al final del objeto, por ejemplo {@code ,"priority":"CRITICAL"}. */
	protected static String equipmentBody(UUID request, UUID equipment, List<UUID> connectors, String type,
			String description, String extra) {
		return "{\"clientRequestId\":\"%s\",\"elementClass\":\"EQUIPMENT\",\"equipmentId\":\"%s\",\"connectorIds\":%s,\"incidentType\":\"%s\",\"description\":\"%s\"%s}"
				.formatted(request, equipment, uuidArray(connectors), type, description, extra);
	}

	protected static String chatbotBody(String description, String extra) {
		return "{\"clientRequestId\":\"%s\",\"elementClass\":\"CHATBOT_SERVICE\",\"incidentType\":\"CHATBOT_SERVICE_FAILURE\",\"description\":\"%s\"%s}"
				.formatted(UUID.randomUUID(), description, extra);
	}

	protected static String siteBody(String siteElementJson, String description, String extra) {
		return "{\"clientRequestId\":\"%s\",\"elementClass\":\"SITE_ELEMENT\",\"siteElement\":%s,\"incidentType\":\"SITE_ELEMENT_FAILURE\",\"description\":\"%s\"%s}"
				.formatted(UUID.randomUUID(), siteElementJson, description, extra);
	}

	protected static String uuidArray(List<UUID> ids) {
		return "[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]";
	}

	protected ResultActions createTicket(Cookie session, String body) throws Exception {
		return post(session, "/tickets", body);
	}

	/** Crea un ticket de equipo con tres datos y devuelve su id; espera un 201. */
	protected UUID createEquipmentTicket(Cookie session, UUID equipment, String type) throws Exception {
		return idOf(createTicket(session, equipmentBody(equipment, type, "Sin comunicacion"))
				.andExpect(status().isCreated()));
	}

	// ----- Tickets de cada clase, hitos, cierre y reapertura (T004) -----

	/** Crea un ticket del servicio de chatbot con tres datos y devuelve su id; espera un 201. */
	protected UUID createChatbotTicket(Cookie session) throws Exception {
		return idOf(createTicket(session, chatbotBody("Chatbot sin respuesta", "")).andExpect(status().isCreated()));
	}

	/** Crea una barrera vehicular de «CC Prueba Norte», con su equipo de referencia, y devuelve su id; espera un 201. */
	protected UUID createSiteTicket(Cookie session) throws Exception {
		String barrier = "{\"kind\":\"VEHICLE_BARRIER\",\"referenceEquipmentId\":\"%s\"}"
				.formatted(Feature010Fixtures.SITE_REFERENCE);
		return idOf(createTicket(session, siteBody(barrier, "No sube", "")).andExpect(status().isCreated()));
	}

	/** Adelanta el T0 del episodio 1 {@code minutesAgo} minutos al pasado, para poder registrar hitos posteriores sin que sean futuros. */
	protected void backdateT0(UUID ticketId, int minutesAgo) {
		jdbc.update("""
				UPDATE inc_milestone SET occurred_at = now() - make_interval(mins => ?)
				WHERE kind = 'T0' AND episode_id = (SELECT id FROM inc_episode WHERE ticket_id = ? AND ordinal = 1)
				""", minutesAgo, ticketId);
	}

	/**
	 * Registra un hito con una hora exacta, minutos despues del T0 del episodio 1: asi la prueba no depende del reloj ni
	 * de la hora en que corre. Una hora anterior al registro es retroactiva y lleva su motivo.
	 */
	protected ResultActions recordMilestoneAt(Cookie session, UUID ticketId, String kind, int minutesAfterT0)
			throws Exception {
		java.time.Instant t0 = jdbc.queryForObject("""
				SELECT m.occurred_at FROM inc_milestone m JOIN inc_episode e ON e.id = m.episode_id
				WHERE e.ticket_id = ? AND e.ordinal = 1 AND m.kind = 'T0'""", java.sql.Timestamp.class, ticketId).toInstant();
		return put(session, "/tickets/" + ticketId + "/episodes/1/milestones/" + kind,
				"{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\"}"
						.formatted(versionOf(ticketId), t0.plusSeconds(minutesAfterT0 * 60L)));
	}

	/** T1 a T3 con un estado explicito: «No aplica» con motivo, sin necesidad de horas. */
	protected void resolveMilestones(UUID ticketId) throws Exception {
		for (String kind : new String[] { "T1", "T2", "T3" }) {
			put(analyst(), "/tickets/" + ticketId + "/episodes/1/milestones/" + kind,
					"{\"version\":%d,\"status\":\"NOT_APPLICABLE\",\"reason\":\"No aplica en la prueba\"}"
							.formatted(versionOf(ticketId))).andExpect(status().isOk());
		}
	}

	/** Cierra el ticket con la version actual; {@code fields} son los campos del cuerpo, sin llaves. */
	protected ResultActions close(Cookie session, UUID ticketId, String fields) throws Exception {
		return post(session, "/tickets/" + ticketId + "/close",
				"{\"version\":%d%s}".formatted(versionOf(ticketId), fields.isEmpty() ? "" : "," + fields));
	}

	/** Reabre el ticket con la version actual; {@code fields} son los campos del cuerpo, sin llaves. */
	protected ResultActions reopen(Cookie session, UUID ticketId, String fields) throws Exception {
		return post(session, "/tickets/" + ticketId + "/reopen", "{\"version\":%d,%s}".formatted(versionOf(ticketId), fields));
	}

	/** El id del ticket de una respuesta de detalle. */
	protected static UUID idOf(ResultActions result) throws Exception {
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(),
				"$.summary.id"));
	}

	protected long versionOf(UUID ticketId) {
		return jdbc.queryForObject("SELECT version FROM inc_ticket WHERE id = ?", Long.class, ticketId);
	}

	protected int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}
}
