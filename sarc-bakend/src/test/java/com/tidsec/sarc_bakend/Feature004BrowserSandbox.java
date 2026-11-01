package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.tidsec.sarc_bakend.modules.identity.domain.model.*;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;

/** QA opt-in. No conecta ni aplica migraciones a la BD dev. */
@EnabledIfSystemProperty(named = "sarc.qa.browser", matches = "true")
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"sarc.security.bcrypt-strength=4",
			"server.address=127.0.0.1",
			"spring.docker.compose.enabled=false"
		})
@Import(TestcontainersConfiguration.class)
class Feature004BrowserSandbox {
	@LocalServerPort int port;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage storage;
	private final Map<String, byte[]> documents = new ConcurrentHashMap<>();

	@Test
	void browserAcceptanceAgainstDisposableDatabase() throws Exception {
		for (Role role : Role.values()) seed(role.name().toLowerCase(), Set.of(role));
		seed("multi", Set.of(Role.ANALISTA, Role.GERENTE));
		when(storage.storeDocument(anyString(), anyString(), any()))
				.thenAnswer(
						inv -> {
							String reference = "sandbox/004/" + UUID.randomUUID();
							documents.put(reference, inv.getArgument(2));
							return reference;
						});
		when(storage.readDocument(anyString()))
				.thenAnswer(inv -> documents.get(inv.getArgument(0)));
		when(storage.documentUrl(anyString()))
				.thenReturn(Optional.of("https://qa.invalid/document"));
		Feature004Fixtures.seed(jdbc, Boolean.getBoolean("sarc.qa.load"));
		Path done = Path.of("target/feature004-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(
				Path.of("target/feature004-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature004-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) Thread.sleep(250);
		assertThat(Files.exists(done)).as("Recorrido QA004 finalizado").isTrue();
	}

	private void seed(String name, Set<Role> roles) {
		String hash = encoder.encode("Prueba-Feature004-2026");
		UserAccount user =
				UserAccount.create(
						new Email(name + "@qa004.example.test"), "QA004 " + name, roles, hash);
		user.changePassword(hash);
		users.save(user);
	}
}
