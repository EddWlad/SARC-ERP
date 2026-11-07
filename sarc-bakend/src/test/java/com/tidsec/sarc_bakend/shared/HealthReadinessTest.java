package com.tidsec.sarc_bakend.shared;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;

/**
 * Salud publica minima en el perfil {@code prod} (FR-035): {@code readiness} responde solo el estado
 * y refleja la base de datos; {@code info} exige sesion. Las variables de base de datos de
 * {@code application-prod.yaml} se sustituyen por valores ficticios (la conexion real viene del
 * contenedor de prueba) y Cloudinary deja de ser obligatorio solo para esta prueba.
 */
@SpringBootTest(properties = {
		"SARC_DB_URL=sin-uso", "SARC_DB_USER=sin-uso", "SARC_DB_PASSWORD=sin-uso",
		"sarc.storage.cloudinary.required=false",
		"spring.datasource.hikari.connection-timeout=1500" })
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthReadinessTest {

	@Autowired MockMvc mvc;
	@Autowired PostgreSQLContainer postgres;

	@Test
	@Order(1)
	void readinessIsUpWithOnlyTheStatusField() throws Exception {
		mvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.*", hasSize(1)));
	}

	@Test
	@Order(2)
	void infoRequiresAnAuthenticatedAdministrator() throws Exception {
		int code = mvc.perform(get("/actuator/info")).andReturn().getResponse().getStatus();
		org.assertj.core.api.Assertions.assertThat(code).isIn(401, 403);
	}

	@Test
	@Order(3)
	void readinessIsDownWhenTheDatabaseStops() throws Exception {
		postgres.stop();
		mvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.*", hasSize(1)));
	}
}
