package com.tidsec.sarc_bakend.modules.identity.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.tidsec.sarc_bakend.support.WebSessions.loginRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;

/** TRD 16.1: los fallos de ingreso se limitan por origen, ademas del bloqueo por cuenta. */
@SpringBootTest(properties = {
		"sarc.security.bcrypt-strength=4",
		"sarc.security.max-failed-logins-per-client=2"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginThrottleApiTest {

	@Autowired MockMvc mvc;

	@Test
	void anOriginThatKeepsFailingGets429UntilItsWindowEnds() throws Exception {
		mvc.perform(loginRequest("uno@eos.com", "clave-equivocada-1")).andExpect(status().isUnauthorized());
		mvc.perform(loginRequest("dos@eos.com", "clave-equivocada-2")).andExpect(status().isUnauthorized());

		mvc.perform(loginRequest("tres@eos.com", "clave-equivocada-3"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
				.andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
		mvc.perform(loginRequest("tres@eos.com", "clave-equivocada-3").with(request -> {
			request.setRemoteAddr("10.20.30.40");
			return request;
		})).andExpect(status().isUnauthorized());
	}
}
