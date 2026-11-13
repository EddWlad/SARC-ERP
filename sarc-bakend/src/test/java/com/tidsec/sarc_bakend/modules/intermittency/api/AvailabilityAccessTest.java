package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

import jakarta.servlet.http.Cookie;

/**
 * La disponibilidad estimada es una excepcion de la etapa de prueba: solo quien tiene el rol Analista o
 * Administrador la recibe, solo o combinado con otros, y se evalua con los roles vigentes (FR-006, FR-007).
 */
class AvailabilityAccessTest extends IntermittencyApiSupport {

	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guayaquil"));
	private static final String RANGE = "?preset=CUSTOM&from=" + TODAY.minusDays(1) + "&to=" + TODAY;

	private Cookie session(Set<Role> roles) {
		return as(roles.toArray(new Role[0]));
	}

	/**
	 * Estas pruebas usan «ayer» y «hoy» de la zona operativa y el servidor lee el reloj real: a minutos de la
	 * medianoche de Guayaquil el dia podria cambiar a mitad de una prueba, asi que no se ejecutan entonces.
	 */
	@BeforeEach
	void notNearMidnight() {
		LocalTime now = LocalTime.now(ZoneId.of("America/Guayaquil"));
		Assumptions.assumeTrue(now.isAfter(LocalTime.of(0, 10)) && now.isBefore(LocalTime.of(23, 50)),
				"Demasiado cerca de la medianoche de Guayaquil");
	}

	@Test
	void administratorAnalystAndTheirCombinationsReceiveItAndTheOthersAreForbidden() throws Exception {
		for (Set<Role> allowed : new Set[] { Set.of(Role.ADMINISTRADOR), Set.of(Role.ANALISTA),
				Set.of(Role.ANALISTA, Role.GERENTE), Set.of(Role.ADMINISTRADOR, Role.ASISTENTE) }) {
			get(session(allowed), "/availability" + RANGE).andExpect(status().isOk());
		}
		for (Set<Role> forbidden : new Set[] { Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
				Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE) }) {
			String body = assertProblem(get(session(forbidden), "/availability" + RANGE), 403, "AVAILABILITY_RESTRICTED")
					.andReturn().getResponse().getContentAsString();
			// Ni cifras ni cobertura: solo el problema.
			assertThat(body).doesNotContain("percent").doesNotContain("coverage").doesNotContain("points");
		}
	}

	@Test
	void withoutASessionItIsUnauthorized() throws Exception {
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get(BASE + "/availability" + RANGE)).andExpect(status().isUnauthorized());
	}

	@Test
	void losingTheAnalystRoleRemovesTheAccessOnTheNextRequest() throws Exception {
		Set<Role> roles = Set.of(Role.ANALISTA, Role.GERENTE);
		Cookie both = session(roles);
		get(both, "/availability" + RANGE).andExpect(status().isOk());

		// Un administrador le quita el rol Analista: la sesion anterior se revoca y la nueva ya no la recibe.
		String email = Feature008Fixtures.emailFor(roles);
		Map<String, Object> user = jdbc.queryForMap("SELECT id, version FROM auth_user WHERE email = ?", email);
		mvc.perform(MockMvcRequestBuilders.patch("/api/v1/users/" + user.get("id")).cookie(as(Role.ADMINISTRADOR))
				.with(WebSessions.xsrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":%d,\"roles\":[\"GERENTE\"]}".formatted(((Number) user.get("version")).longValue())))
				.andExpect(status().isOk());

		get(both, "/availability" + RANGE).andExpect(status().isUnauthorized());
		Cookie renewed = WebSessions.login(mvc, email, Feature008Fixtures.PASSWORD);
		assertProblem(get(renewed, "/availability" + RANGE), 403, "AVAILABILITY_RESTRICTED");
		// Lo que si es de consulta sigue disponible.
		get(renewed, "/summary" + RANGE).andExpect(status().isOk());
	}

	@Test
	void noParameterOfTheSummaryExposesTheAvailabilityToAnyRole() throws Exception {
		for (Set<Role> roles : Feature008Fixtures.ROLE_SETS) {
			String body = get(session(roles), "/summary" + RANGE + "&availability=true&includeAvailability=true&withAvailability=true")
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString().toLowerCase();

			assertThat(body).doesNotContain("availab").doesNotContain("disponib").doesNotContain("coveredday")
					.doesNotContain("evaluatedminutes");
		}
	}
}
