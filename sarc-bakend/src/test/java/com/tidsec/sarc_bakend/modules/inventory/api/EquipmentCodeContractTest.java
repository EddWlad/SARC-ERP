package com.tidsec.sarc_bakend.modules.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tidsec.sarc_bakend.support.WebSessions.xsrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;


import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentResponse;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentSummaryResponse;
import com.tidsec.sarc_bakend.modules.consumption.api.response.EquipmentRefResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentCodeContractTest {
	@Autowired MockMvc mvc;
	@MockitoBean FileStorage fileStorage;

	@ParameterizedTest
	@ValueSource(strings = { "ADMINISTRADOR", "ANALISTA" })
	void authorizedOperationsExposeNullAndAdministrativePatchDoesNotChangeUsage(String role) throws Exception {
		String serial = UUID.randomUUID().toString();
		String json = """
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"Contrato",
				"serial":"%s","uidPuk":"%s","usageStatus":"FREE",
				"connectorTypeIds":["00000000-0000-0000-0000-000000000c01"]}
				""".formatted(serial,serial);
		String response = mvc.perform(post("/api/v1/equipment").with(user("qa").roles(role)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.assignedCode").isEmpty())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(response,"$.id");
		mvc.perform(get("/api/v1/equipment/"+id).with(user("qa").roles(role)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.assignedCode").isEmpty())
				.andExpect(jsonPath("$.version").value(0));
		mvc.perform(get("/api/v1/equipment").param("text",serial).with(user("qa").roles(role)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.content[0].assignedCode").isEmpty());
		mvc.perform(patch("/api/v1/equipment/"+id+"/status").with(user("qa").roles(role)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"status\":\"INACTIVE\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.usageStatus").value("FREE"))
				.andExpect(jsonPath("$.assignedCode").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { "GERENTE", "SUPERVISOR", "ASISTENTE" })
	void queryRolesCannotUseTheRelaxedRuleToWrite(String role) throws Exception {
		mvc.perform(post("/api/v1/equipment").with(user("qa").roles(role)).with(xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("""
				{"network":"KIA","type":"CHARGING_POINT","brand":"WALLBOX","name":"No autorizado",
				"serial":"QA-DENIED","uidPuk":"QA-DENIED","usageStatus":"FREE",
				"connectorTypeIds":["00000000-0000-0000-0000-000000000c01"]}
				""")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/equipment").with(user("qa").roles(role))).andExpect(status().isOk());
	}

	@Test
	void responseRecordsAndPublishedContractDeclareNullableCode() throws Exception {
		String contract = Files.readString(Path.of("docs/api/openapi.json"));
		for (Class<?> response : new Class<?>[] { EquipmentResponse.class, EquipmentSummaryResponse.class,
				EquipmentRefResponse.class }) {
			var field = Arrays.stream(response.getRecordComponents())
					.filter(component -> component.getName().equals("assignedCode")).findFirst().orElseThrow();
			Schema annotation = response.getDeclaredField(field.getName()).getAnnotation(Schema.class);
			assertThat(annotation).as(response.getSimpleName()).isNotNull();
			assertThat(annotation.nullable()).isTrue();
			Object type = JsonPath.read(contract,
					"$.components.schemas." + response.getSimpleName() + ".properties.assignedCode.type");
			assertThat(type.toString()).contains("null");
		}
	}
}
