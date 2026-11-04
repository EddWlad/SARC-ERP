package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
import com.tidsec.sarc_bakend.support.Feature005Fixtures;

/** QA opt-in. No conecta ni aplica migraciones a la BD dev. */
@EnabledIfSystemProperty(named = "sarc.qa.browser", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"sarc.security.bcrypt-strength=4", "server.address=127.0.0.1", "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
class Feature005BrowserSandbox {

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

	private final Map<String, byte[]> documents = new ConcurrentHashMap<>();

	@Test
	void browserAcceptanceAgainstDisposableDatabase() throws Exception {
		Feature005Fixtures.ROLE_SETS.forEach(this::seed);
		when(storage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> {
			String reference = "sandbox/005/" + UUID.randomUUID();
			documents.put(reference, invocation.getArgument(2));
			return reference;
		});
		when(storage.readDocument(anyString())).thenAnswer(invocation -> documents.get(invocation.getArgument(0)));
		when(storage.documentUrl(anyString())).thenReturn(Optional.of("https://qa.invalid/document"));
		Feature005Fixtures.seedInventory(jdbc);
		Path done = Path.of("target/feature005-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(Path.of("target/feature005-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature005-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
		}
		assertThat(Files.exists(done)).as("Recorrido QA005 finalizado").isTrue();
	}

	private void seed(Set<Role> roles) {
		String name = roles.stream().map(role -> role.name().toLowerCase()).sorted().collect(Collectors.joining("-"));
		String hash = encoder.encode("Prueba-Feature005-2026");
		UserAccount user = UserAccount.create(new Email(name + "@qa005.example.test"), "QA005 " + name, roles, hash);
		user.changePassword(hash);
		users.save(user);
	}
}
