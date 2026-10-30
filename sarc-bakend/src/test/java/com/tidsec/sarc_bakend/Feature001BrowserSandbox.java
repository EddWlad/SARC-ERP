package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

/**
 * Sandbox optativo para QA en navegador: Testcontainers y storage simulado, nunca la BD local.
 * No pertenece al patron *Test de la suite habitual. Ejecutar explicitamente con -Dsarc.qa.browser=true.
 * El navegador deja dos equipos; al finalizar, el operador crea target/feature001-browser.done.
 */
@EnabledIfSystemProperty(named = "sarc.qa.browser", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = { "sarc.security.bcrypt-strength=4", "server.address=127.0.0.1" })
@Import(TestcontainersConfiguration.class)
class Feature001BrowserSandbox {

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FileStorage storage;

    @Test
    void browserAcceptanceAgainstDisposableDatabase() throws Exception {
        String password = "Prueba-Feature001-2026";
        UserAccount user = UserAccount.create(new Email("qa-feature001@example.test"), "QA Feature 001",
                Set.of(Role.ANALISTA), encoder.encode(password));
        user.changePassword(encoder.encode(password));
        users.save(user);
        when(storage.storeDocument(anyString(), anyString(), any())).thenReturn("sandbox/feature001/report");
        Path done = Path.of("target/feature001-browser.done");
        Files.deleteIfExists(done);
        Files.writeString(Path.of("target/feature001-browser-proxy.json"),
                "{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
        Files.writeString(Path.of("target/feature001-browser.ready"), Integer.toString(port));
        Instant deadline = Instant.now().plus(Duration.ofMinutes(15));
        while (!Files.exists(done) && Instant.now().isBefore(deadline)) {
            Thread.sleep(500);
        }
        assertThat(Files.exists(done)).as("Recorrido de navegador finalizado").isTrue();
        assertThat(jdbc.queryForObject("select count(*) from asset_equipment", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from asset_equipment where usage_status='FREE' "
                + "and assigned_code is null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from asset_equipment where usage_status='FREE' "
                + "and assigned_code='654321'", Integer.class)).isEqualTo(1);
    }
}
