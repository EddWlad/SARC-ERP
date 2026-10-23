package com.tidsec.sarc_bakend.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El contrato REST publicado vive en docs/api/openapi.json y de el se generan los tipos del
 * frontend. Esta prueba falla si el codigo y el archivo no coinciden. Para actualizarlo:
 * {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true} y luego, en el frontend,
 * {@code npm run api:types}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

	private static final Path CONTRACT = Path.of("docs", "api", "openapi.json");
	private static final ObjectMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

	@Autowired MockMvc mvc;

	@Test
	@WithMockUser(roles = "ADMINISTRADOR")
	void publishedContractMatchesTheCode() throws Exception {
		String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		JsonNode current = JSON.readTree(body);
		// La URL del servidor depende del entorno de la prueba; no es parte del contrato.
		((tools.jackson.databind.node.ObjectNode) current).remove("servers");

		if (Boolean.getBoolean("openapi.update")) {
			Files.createDirectories(CONTRACT.getParent());
			Files.writeString(CONTRACT, JSON.writeValueAsString(current) + System.lineSeparator());
		}

		assertThat(Files.exists(CONTRACT))
				.as("Falta %s: ejecutar con -Dopenapi.update=true", CONTRACT).isTrue();
		assertThat(current).as("El contrato cambio: regenerar %s y los tipos del frontend", CONTRACT)
				.isEqualTo(JSON.readTree(Files.readString(CONTRACT)));
	}
}
