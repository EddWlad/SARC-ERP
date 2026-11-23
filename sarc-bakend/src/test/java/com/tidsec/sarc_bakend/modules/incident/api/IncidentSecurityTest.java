package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/**
 * Matriz de seguridad de Incidencias (FR-090 a FR-093, SC-008): los cinco roles y la combinacion Analista y Gerente contra
 * cada endpoint de contracts/incidents-api.md. Los editores (Administrador y Analista) leen y escriben; Gerente,
 * Supervisor y Asistente solo leen y ninguna de sus escrituras cambia una tabla. La configuracion de la numeracion es solo
 * del Administrador. Sin sesion responde 401 y una escritura sin token CSRF, 403.
 */
class IncidentSecurityTest extends IncidentApiSupport {

	private static final List<String> TABLES = List.of("inc_ticket", "inc_episode", "inc_milestone",
			"inc_milestone_revision", "inc_event", "inc_evidence", "inc_communication", "inc_visit", "inc_ticket_relation",
			"inc_ticket_connector", "inc_ticket_numbering", "inc_reference_version");

	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000042");

	/** Un endpoint de escritura con un cuerpo valido para llegar a la autorizacion del caso de uso. */
	private record Write(HttpMethod method, String path, String body) {
	}

	private List<Write> writes(UUID ticket) {
		long version = versionOf(ticket);
		return List.of(
				new Write(HttpMethod.POST, "/tickets", equipmentBody(Feature010Fixtures.JAC_POINT, "DISCONNECTION", "Seguridad")),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/relations",
						"{\"version\":%d,\"relatedTicketId\":\"%s\",\"kind\":\"COMMON_FAILURE\"}".formatted(version, OTHER)),
				new Write(HttpMethod.DELETE, "/tickets/" + ticket + "/relations/" + OTHER + "?version=" + version, null),
				new Write(HttpMethod.PUT, "/tickets/" + ticket + "/episodes/1/milestones/T1",
						"{\"version\":%d,\"status\":\"RECORDED\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/events",
						"{\"version\":%d,\"type\":\"OBSERVATION\",\"text\":\"Nota\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/events/" + OTHER + "/annul",
						"{\"version\":%d,\"reason\":\"Error\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/priority",
						"{\"version\":%d,\"priority\":\"CRITICAL\",\"reason\":\"Urgente\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/responsible",
						"{\"version\":%d,\"responsibleId\":\"%s\"}".formatted(version, OTHER)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/handover",
						"{\"version\":%d,\"incomingResponsibleId\":\"%s\",\"currentStatus\":\"Estado\",\"pending\":\"Nada\",\"closureCriterion\":\"Criterio\"}".formatted(version, OTHER)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/reclassify",
						"{\"version\":%d,\"incidentType\":\"WAIT_FOR_CONNECTION\",\"reason\":\"Otro tipo\"}".formatted(version)),
				new Write(HttpMethod.PATCH, "/tickets/" + ticket,
						"{\"version\":%d,\"description\":\"Cambiada\",\"justification\":\"Motivo\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/close",
						"{\"version\":%d,\"result\":\"REGISTERED_IN_ERROR\",\"causeKind\":\"UNDETERMINED\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/reopen",
						"{\"version\":%d,\"reason\":\"Volvio\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/communications",
						"{\"version\":%d,\"channel\":\"EMAIL\",\"kind\":\"INITIAL\",\"sentAt\":\"2026-01-01T10:00:00Z\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/visits",
						"{\"version\":%d,\"providerName\":\"Proveedor\"}".formatted(version)),
				new Write(HttpMethod.POST, "/tickets/" + ticket + "/visits/" + OTHER + "/stage",
						"{\"version\":%d,\"stage\":\"ASSIGNED\"}".formatted(version)),
				new Write(HttpMethod.PUT, "/numbering", "{\"startNumber\":9001,\"environment\":\"TEST\",\"confirmation\":\"TICKET-9001\"}"));
	}

	private MockHttpServletRequestBuilder request(Write write, Cookie session, boolean withCsrf) {
		MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(write.method(), BASE + write.path());
		if (session != null) {
			builder.cookie(session);
		}
		if (withCsrf) {
			builder.with(WebSessions.xsrf());
		}
		if (write.body() != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(write.body());
		}
		return builder;
	}

	private List<Integer> counts() {
		return TABLES.stream().map(this::count).toList();
	}

	private static boolean isEditor(Set<Role> roles) {
		return roles.contains(Role.ADMINISTRADOR) || roles.contains(Role.ANALISTA);
	}

	// ----- Escrituras -----

	@Test
	void everyWriteIsForbiddenToTheReadOnlyRolesAndChangesNoTable() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		List<Write> writes = writes(ticket);
		List<Integer> before = counts();

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			if (isEditor(roles)) {
				continue;
			}
			for (Write write : writes) {
				MvcResult result = mvc.perform(request(write, as(roles.toArray(Role[]::new)), true)).andReturn();
				assertThat(result.getResponse().getStatus()).as("%s %s como %s", write.method(), write.path(), roles)
						.isEqualTo(403);
			}
		}

		assertThat(counts()).as("ninguna tabla cambia (SC-008)").isEqualTo(before);
	}

	@Test
	void theEditorsReachTheUseCasesAndOnlyTheAdministratorConfiguresTheNumbering() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			if (!isEditor(roles)) {
				continue;
			}
			for (Write write : writes(ticket)) {
				int status = mvc.perform(request(write, as(roles.toArray(Role[]::new)), true)).andReturn().getResponse().getStatus();
				boolean numbering = write.path().equals("/numbering");
				if (numbering && !roles.contains(Role.ADMINISTRADOR)) {
					assertThat(status).as("numeracion como %s", roles).isEqualTo(403);
				}
				else {
					assertThat(status).as("%s %s como %s", write.method(), write.path(), roles).isNotIn(401, 403);
				}
			}
		}
	}

	@Test
	void withoutASessionEveryEndpointAnswers401AndWithoutTheCsrfTokenEveryWriteAnswers403() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		List<Integer> before = counts();

		for (Write write : writes(ticket)) {
			assertThat(mvc.perform(request(write, null, true)).andReturn().getResponse().getStatus())
					.as("sin sesion: %s %s", write.method(), write.path()).isEqualTo(401);
			assertThat(mvc.perform(request(write, analyst(), false)).andReturn().getResponse().getStatus())
					.as("sin CSRF: %s %s", write.method(), write.path()).isEqualTo(403);
		}
		for (String path : List.of("/tickets", "/tickets/" + ticket, "/tickets/" + ticket + "/message", "/numbering",
				"/assets", "/responsibles", "/providers", "/summary?from=2026-01-01&to=2026-01-31",
				"/reports/control?format=PDF", "/reports/management?format=PDF&from=2026-01-01&to=2026-01-31")) {
			mvc.perform(MockMvcRequestBuilders.get(BASE + path)).andExpect(status().isUnauthorized());
		}

		assertThat(counts()).isEqualTo(before);
	}

	// ----- Lecturas -----

	@Test
	void theFiveRolesReadTicketsMessagesNumberingSummaryAndReportsButOnlyEditorsReadAssetsResponsiblesProvidersAndOpenMatches()
			throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		List<String> readers = List.of("/tickets", "/tickets/" + ticket, "/tickets/" + ticket + "/message", "/numbering",
				"/summary?from=2026-01-01&to=2026-01-31", "/reports/control?format=XLSX",
				"/reports/management?format=XLSX&from=2026-01-01&to=2026-01-31");
		List<String> editorsOnly = List.of("/assets?search=QA", "/responsibles", "/providers",
				"/tickets/open-matches?elementClass=EQUIPMENT&equipmentId=" + Feature010Fixtures.KIA_STATION);

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			Cookie session = as(roles.toArray(Role[]::new));
			for (String path : readers) {
				get(session, path).andExpect(status().isOk());
			}
			for (String path : editorsOnly) {
				int expected = isEditor(roles) ? 200 : 403;
				assertThat(get(session, path).andReturn().getResponse().getStatus()).as("%s como %s", path, roles)
						.isEqualTo(expected);
			}
		}
	}

	// ----- Acceso directo -----

	@Test
	void changingTheMethodOrTheBodyDoesNotLetAReadOnlyRoleWrite() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		List<Integer> before = counts();
		Cookie reader = as(Role.GERENTE);

		for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.POST)) {
			for (String path : List.of("/tickets/" + ticket, "/tickets/" + ticket + "/close", "/tickets/" + ticket + "/events",
					"/tickets/" + ticket + "/episodes/1/milestones/T4", "/numbering", "/tickets")) {
				for (String body : List.of("{}", "{\"version\":0,\"status\":\"RECORDED\"}", "{\"result\":\"RECOVERED\"}")) {
					int status = mvc.perform(request(new Write(method, path, body), reader, true)).andReturn().getResponse()
							.getStatus();
					assertThat(status).as("%s %s", method, path).isBetween(400, 499);
				}
			}
		}

		assertThat(counts()).isEqualTo(before);
	}

	@Test
	void aUserWhoLosesTheRoleBetweenTwoRequestsIsEvaluatedWithoutIt() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		Write note = new Write(HttpMethod.POST, "/tickets/" + ticket + "/events",
				"{\"version\":%d,\"type\":\"OBSERVATION\",\"text\":\"Nota\"}".formatted(versionOf(ticket)));
		assertThat(mvc.perform(request(note, analyst(), true)).andReturn().getResponse().getStatus()).isEqualTo(200);

		// El Administrador le quita el rol por la API de usuarios, que revoca sus sesiones abiertas.
		UserAccount user = users.findById(userId(Role.ANALISTA)).orElseThrow();
		mvc.perform(MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId()).cookie(admin()).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"roles\":[\"GERENTE\"]}".formatted(user.getVersion()))).andExpect(status().isOk());
		Write again = new Write(HttpMethod.POST, "/tickets/" + ticket + "/events",
				"{\"version\":%d,\"type\":\"OBSERVATION\",\"text\":\"Otra\"}".formatted(versionOf(ticket)));
		List<Integer> before = counts();

		assertThat(mvc.perform(request(again, analyst(), true)).andReturn().getResponse().getStatus())
				.as("la sesion anterior ya no escribe").isIn(401, 403);
		Cookie fresh = WebSessions.login(mvc, Feature010Fixtures.emailFor(Set.of(Role.ANALISTA)), Feature010Fixtures.PASSWORD);
		assertThat(mvc.perform(request(again, fresh, true)).andReturn().getResponse().getStatus())
				.as("al volver a entrar ya solo lee").isEqualTo(403);
		get(fresh, "/tickets/" + ticket).andExpect(status().isOk());
		assertThat(counts()).isEqualTo(before);
	}

	@Test
	void theReadOnlyRolesSeeNoEditActionsInTheDetail() throws Exception {
		UUID ticket = createEquipmentTicket(analyst(), Feature010Fixtures.KIA_STATION, "DISCONNECTION");
		List<String> readOnly = new ArrayList<>();

		for (Cookie session : readOnlyRoles()) {
			readOnly.add(get(session, "/tickets/" + ticket).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString());
		}

		assertThat(readOnly).allSatisfy(body -> assertThat(body).contains("\"allowedActions\":[]"));
		assertThat(get(analyst(), "/tickets/" + ticket).andReturn().getResponse().getContentAsString())
				.doesNotContain("\"allowedActions\":[]");
	}
}
