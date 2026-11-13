package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/**
 * Matriz de roles sobre cada endpoint de contracts/intermittency-api.md y sobre la descarga de informes archivados,
 * por acceso directo sin la interfaz (SC-007): los cinco roles y las combinaciones que prueban la union de permisos
 * (FR-004, FR-005, FR-006, FR-007). Quien solo consulta no puede modificar nada, ni siquiera sabiendo la ruta.
 */
class IntermittencySecurityTest extends IntermittencyApiSupport {

	/** Quien puede ver el resultado de cada endpoint (los roles T, E y D del contrato). */
	private enum Access {
		/** Cualquier usuario autenticado. */
		ANYONE,
		/** Administrador o Analista. */
		EDITORS,
		/** Quien tiene el permiso de disponibilidad: hoy Administrador o Analista. */
		AVAILABILITY
	}

	private record Endpoint(HttpMethod method, String path, String body, Access access) {

		@Override
		public String toString() {
			return method + " " + path;
		}
	}

	private static final UUID UNKNOWN = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
	private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("America/Guayaquil")).minusDays(1);
	private static final String JUSTIFIED = "{\"version\":0,\"justification\":\"Prueba de acceso\"}";

	private static boolean editor(Set<Role> roles) {
		return roles.contains(Role.ADMINISTRADOR) || roles.contains(Role.ANALISTA);
	}

	private static Endpoint any(HttpMethod method, String path) {
		return new Endpoint(method, path, null, Access.ANYONE);
	}

	private static Endpoint editors(HttpMethod method, String path, String body) {
		return new Endpoint(method, path, body, Access.EDITORS);
	}

	/** Cada ruta del contrato, con un identificador conocido de una ventana confirmada donde hace falta. */
	private List<Endpoint> endpoints(UUID window) {
		String range = "?preset=CUSTOM&from=" + YESTERDAY + "&to=" + YESTERDAY.plusDays(1);
		String pointTest = "{\"from\":\"" + YESTERDAY + "\",\"to\":\"" + YESTERDAY.plusDays(1)
				+ "\",\"justification\":\"Prueba de acceso\"}";
		String correction = "{\"version\":0,\"comment\":\"x\",\"justification\":\"Prueba de acceso\"}";
		String row = "/import-batches/" + UNKNOWN + "/rows/" + UNKNOWN;
		List<Endpoint> all = new ArrayList<>();
		// Puntos
		all.add(any(HttpMethod.GET, "/points?query=punto"));
		all.add(editors(HttpMethod.POST, "/points/" + Feature008Fixtures.BLOCKED + "/test", pointTest));
		all.add(editors(HttpMethod.POST, "/points/" + Feature008Fixtures.BLOCKED + "/untest", pointTest));
		// Ventanas
		all.add(editors(HttpMethod.POST, "/windows", capture(Feature008Fixtures.FREE, window(at("08:00"), at("08:30")))));
		all.add(any(HttpMethod.GET, "/windows?size=5"));
		all.add(any(HttpMethod.GET, "/windows/" + window));
		all.add(editors(HttpMethod.PATCH, "/windows/" + window, correction));
		for (String action : List.of("close", "confirm-review", "test", "untest", "annul")) {
			all.add(editors(HttpMethod.POST, "/windows/" + window + "/" + action,
					"close".equals(action) ? "{\"version\":0,\"endAt\":\"" + at("09:00") + "\"}" : JUSTIFIED));
		}
		// Lotes de importacion
		all.add(editors(HttpMethod.POST, "/import-batches", null));
		all.add(editors(HttpMethod.POST, "/import-batches/paste",
				"{\"text\":\"Fecha Codigo cargador Inicio 1 Fin 1\"}"));
		all.add(editors(HttpMethod.GET, "/import-batches", null));
		all.add(editors(HttpMethod.GET, "/import-batches/" + UNKNOWN, null));
		all.add(editors(HttpMethod.GET, "/import-batches/" + UNKNOWN + "/rows", null));
		all.add(editors(HttpMethod.PATCH, row, "{\"version\":0,\"edits\":[]}"));
		all.add(editors(HttpMethod.POST, row + "/link", "{\"version\":0,\"equipmentId\":\"" + Feature008Fixtures.BLOCKED + "\"}"));
		all.add(editors(HttpMethod.POST, row + "/decision", "{\"version\":0,\"decision\":\"SKIP\",\"skipReason\":\"x\"}"));
		all.add(editors(HttpMethod.POST, row + "/resolve", "{\"version\":0,\"decision\":\"SKIP\",\"skipReason\":\"x\"}"));
		for (String action : List.of("confirm", "discard", "retry")) {
			all.add(editors(HttpMethod.POST, "/import-batches/" + UNKNOWN + "/" + action, "{\"version\":0}"));
		}
		all.add(editors(HttpMethod.GET, "/pending-rows", null));
		// Dias revisados
		all.add(editors(HttpMethod.GET, "/reviewed-days", null));
		all.add(editors(HttpMethod.PUT, "/reviewed-days/" + YESTERDAY.minusDays(3), null));
		all.add(editors(HttpMethod.POST, "/reviewed-days/" + YESTERDAY.minusDays(9) + "/withdraw", JUSTIFIED));
		// Consulta, informes y disponibilidad
		all.add(any(HttpMethod.GET, "/summary" + range));
		all.add(any(HttpMethod.GET, "/report" + range + "&format=PDF"));
		all.add(any(HttpMethod.GET, "/report" + range + "&format=XLSX"));
		all.add(new Endpoint(HttpMethod.GET, "/availability" + range, null, Access.AVAILABILITY));
		return all;
	}

	private RequestBuilder request(Endpoint endpoint, Cookie session) {
		AbstractMockHttpServletRequestBuilder<?> builder;
		if (endpoint.method() == HttpMethod.POST && "/import-batches".equals(endpoint.path())) {
			builder = MockMvcRequestBuilders.multipart(BASE + endpoint.path())
					.file(new MockMultipartFile("file", "vacio.csv", "text/csv", new byte[0]));
		}
		else {
			builder = MockMvcRequestBuilders.request(endpoint.method(), BASE + endpoint.path());
		}
		if (endpoint.body() != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
		}
		if (session != null) {
			builder.cookie(session);
		}
		return builder.with(WebSessions.xsrf());
	}

	private int statusOf(Endpoint endpoint, Cookie session) throws Exception {
		return mvc.perform(request(endpoint, session)).andReturn().getResponse().getStatus();
	}

	private Cookie session(Set<Role> roles) {
		return as(roles.toArray(new Role[0]));
	}

	private UUID confirmedWindow() {
		return insertWindow(Feature008Fixtures.BLOCKED, at(YESTERDAY.toString(), "10:00"),
				at(YESTERDAY.toString(), "10:30"), "CONFIRMED");
	}

	@Test
	void withoutASessionEveryEndpointIsUnauthorized() throws Exception {
		for (Endpoint endpoint : endpoints(confirmedWindow())) {
			assertThat(statusOf(endpoint, null)).as(endpoint.toString()).isEqualTo(401);
		}
	}

	@Test
	void everyRoleAndCombinationGetsExactlyWhatItsPermissionsAllow() throws Exception {
		List<Endpoint> endpoints = endpoints(confirmedWindow());
		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			Cookie session = session(roles);
			for (Endpoint endpoint : endpoints) {
				int status = statusOf(endpoint, session);
				String context = endpoint + " con " + roles;
				switch (endpoint.access()) {
					case ANYONE -> assertThat(status).as(context).isEqualTo(200);
					case EDITORS -> {
						if (editor(roles)) {
							// Pasa la autorizacion: el resultado depende del dato, pero nunca 401, 403 ni error de servidor.
							assertThat(status).as(context).isBetween(200, 499).isNotIn(401, 403);
						}
						else {
							assertThat(status).as(context).isEqualTo(403);
						}
					}
					case AVAILABILITY -> assertThat(status).as(context).isEqualTo(editor(roles) ? 200 : 403);
				}
			}
		}
	}

	@Test
	void whoOnlyConsultsCannotChangeAnythingEvenKnowingTheRoutes() throws Exception {
		UUID window = confirmedWindow();
		int windows = count("true");
		int days = jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day", Integer.class);
		int batches = jdbc.queryForObject("SELECT count(*) FROM itm_import_batch", Integer.class);
		int revisions = jdbc.queryForObject("SELECT count(*) FROM itm_window_revision", Integer.class);
		List<Endpoint> endpoints = endpoints(window);

		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			if (editor(roles)) {
				continue;
			}
			Cookie session = session(roles);
			for (Endpoint endpoint : endpoints) {
				if (endpoint.access() == Access.EDITORS) {
					assertThat(statusOf(endpoint, session)).as(endpoint + " con " + roles).isEqualTo(403);
				}
			}
		}

		assertThat(count("true")).isEqualTo(windows);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day", Integer.class)).isEqualTo(days);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_import_batch", Integer.class)).isEqualTo(batches);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_window_revision", Integer.class)).isEqualTo(revisions);
		assertThat(jdbc.queryForObject("SELECT state FROM itm_window WHERE id = ?", String.class, window))
				.isEqualTo("CONFIRMED");
	}

	@Test
	void anArchivedReportWithOperationsIsDownloadableOnlyByWhoHasThePermission() throws Exception {
		String range = "?preset=CUSTOM&from=" + YESTERDAY + "&to=" + YESTERDAY.plusDays(1) + "&format=PDF";
		java.util.Map<String, byte[]> stored = new java.util.HashMap<>();
		org.mockito.Mockito.when(fileStorage.storeDocument(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
					String reference = "qa008/report-" + stored.size();
					stored.put(reference, invocation.getArgument(2));
					return reference;
				});
		org.mockito.Mockito.when(fileStorage.readDocument(org.mockito.ArgumentMatchers.anyString()))
				.thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));
		String editorReport = get(analyst(), "/report" + range).andReturn().getResponse().getHeader("X-Report-Id");
		String officialReport = get(as(Role.GERENTE), "/report" + range).andReturn().getResponse().getHeader("X-Report-Id");

		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			Cookie session = session(roles);
			int withOperations = mvc.perform(MockMvcRequestBuilders.get("/api/v1/reports/issued/" + editorReport + "/download")
					.cookie(session)).andReturn().getResponse().getStatus();
			int official = mvc.perform(MockMvcRequestBuilders.get("/api/v1/reports/issued/" + officialReport + "/download")
					.cookie(session)).andReturn().getResponse().getStatus();

			assertThat(withOperations).as("copia con operaciones para %s", roles).isEqualTo(editor(roles) ? 200 : 403);
			assertThat(official).as("copia oficial para %s", roles).isEqualTo(200);
		}
	}
}
