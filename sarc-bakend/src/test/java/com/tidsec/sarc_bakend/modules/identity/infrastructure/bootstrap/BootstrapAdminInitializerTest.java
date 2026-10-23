package com.tidsec.sarc_bakend.modules.identity.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;

@SpringBootTest(properties = {
		"sarc.security.bcrypt-strength=4",
		"sarc.bootstrap-admin.email=Root@EOS.com",
		"sarc.bootstrap-admin.password=clave-inicial-segura",
		"sarc.bootstrap-admin.full-name=Root"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BootstrapAdminInitializerTest {

	@Autowired UserRepository users;
	@Autowired MockMvc mvc;

	@Test
	void firstAdministratorIsCreatedOnceWithTemporaryPassword() throws Exception {
		UserAccount root = users.findByEmail("root@eos.com").orElseThrow();

		assertThat(root.hasRole(Role.ADMINISTRADOR)).isTrue();
		assertThat(root.isMustChangePassword()).isTrue();
		mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"root@eos.com\",\"password\":\"clave-inicial-segura\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true));
	}
}
