package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/**
 * QA opt-in de Feature 010: base desechable de Testcontainers con equipos sinteticos, un usuario por rol y la numeracion
 * de prueba {@code TEST} desde 9001, para recorrer en el navegador el registro, el listado y el detalle de tickets. No
 * toca la base de desarrollo ni la de compose. Se ejecuta con {@code -Dsarc.qa.browser010=true}; el recorrido crea
 * {@code target/feature010-browser.done} al terminar.
 */
@EnabledIfSystemProperty(named = "sarc.qa.browser010", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"sarc.security.bcrypt-strength=4", "server.address=127.0.0.1", "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
class Feature010BrowserSandbox {

	@LocalServerPort
	int port;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	FileStorage storage;

	@Test
	void browserAcceptanceAgainstDisposableDatabase() throws Exception {
		for (Role role : new Role[] { Role.ADMINISTRADOR, Role.ANALISTA, Role.GERENTE }) {
			UserAccount user = UserAccount.create(new Email(role.name().toLowerCase() + "@qa010.example.test"),
					"QA010 " + role, Set.of(role), encoder.encode(Feature010Fixtures.PASSWORD));
			user.changePassword(encoder.encode(Feature010Fixtures.PASSWORD));
			users.save(user);
		}
		// Almacenamiento simulado: la carga de evidencias guarda una referencia y la descarga redirige a una URL ficticia.
		when(storage.storeDocument(anyString(), anyString(), any(byte[].class))).thenReturn("sandbox-ref");
		when(storage.documentUrl(anyString())).thenReturn(Optional.of("https://files.example.test/signed"));
		Feature010Fixtures.seedInventory(jdbc);
		Feature010Fixtures.configureNumbering(jdbc);

		Path done = Path.of("target/feature010-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(Path.of("target/feature010-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature010-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
		}
		assertThat(Files.exists(done)).as("Recorrido QA010 finalizado").isTrue();
	}
}
