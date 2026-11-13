package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Entry;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Layout;
import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Pair;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * QA opt-in (T124): levanta la aplicacion sobre una base desechable de Testcontainers, con usuarios por rol, puntos
 * KIA, ventanas en todos los estados y dias revisados sinteticos, y espera a que el recorrido en el navegador
 * termine. No conecta ni aplica migraciones a la base de desarrollo ni a la de compose. Se ejecuta con
 * {@code -Dsarc.qa.browser008=true}; el recorrido crea {@code target/feature008-browser.done} al terminar.
 */
@EnabledIfSystemProperty(named = "sarc.qa.browser008", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"sarc.security.bcrypt-strength=4", "server.address=127.0.0.1", "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
class Feature008BrowserSandbox {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

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
		Feature008Fixtures.ROLE_SETS.forEach(this::seedUser);
		when(storage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> {
			String reference = "sandbox/008/" + UUID.randomUUID();
			documents.put(reference, invocation.getArgument(2));
			return reference;
		});
		when(storage.readDocument(anyString())).thenAnswer(invocation -> documents.get(invocation.getArgument(0)));
		when(storage.documentUrl(anyString())).thenReturn(Optional.of("https://qa.invalid/document"));
		Feature008Fixtures.seedInventory(jdbc);
		seedIntermittencies();
		writeOperationalFiles();

		Path done = Path.of("target/feature008-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(Path.of("target/feature008-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature008-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
		}
		assertThat(Files.exists(done)).as("Recorrido QA008 finalizado").isTrue();
	}

	private void seedUser(Set<Role> roles) {
		String hash = encoder.encode(Feature008Fixtures.PASSWORD);
		UserAccount user = UserAccount.create(new Email(Feature008Fixtures.emailFor(roles)),
				"QA008 " + roles.stream().map(role -> role.name().toLowerCase()).sorted().collect(Collectors.joining("-")),
				roles, hash);
		user.changePassword(hash);
		users.save(user);
	}

	/**
	 * Quince dias de ventanas con todos los estados, varias de mas de un dia calendario y dias revisados, para que
	 * el tablero, los repartos, el Top 10, la revision y la disponibilidad tengan algo que mostrar.
	 */
	private void seedIntermittencies() {
		LocalDate today = LocalDate.now(ZONE);
		UUID admin = jdbc.queryForObject("SELECT id FROM auth_user WHERE email = ?", UUID.class,
				Feature008Fixtures.emailFor(Set.of(Role.ADMINISTRADOR)));
		for (int back = 1; back <= 14; back++) {
			LocalDate day = today.minusDays(back);
			window(Feature008Fixtures.BLOCKED, day, "08:00", day, "08:40", "CONFIRMED");
			window(Feature008Fixtures.BLOCKED, day, "14:00", day, "14:30", "CONFIRMED");
			if (back % 2 == 0) {
				window(Feature008Fixtures.FREE, day, "10:00", day, "10:15", "CONFIRMED");
			}
			if (back % 3 == 0) {
				window(Feature008Fixtures.SECOND_BLOCKED, day, "22:30", day.plusDays(1), "00:20", "CONFIRMED");
			}
		}
		window(Feature008Fixtures.FREE_WITHOUT_CODE, today.minusDays(3), "07:00", today.minusDays(3), "11:30", "REVIEW");
		window(Feature008Fixtures.TEST_POINT, today.minusDays(5), "09:00", today.minusDays(5), "09:45", "TEST");
		window(Feature008Fixtures.SECOND_BLOCKED, today.minusDays(2), "16:00", null, null, "OPEN");
		for (int back = 1; back <= 10; back++) {
			if (back != 4) {
				jdbc.update("INSERT INTO itm_reviewed_day (day, active, marked_by, marked_at, version) VALUES (?, true, ?, now(), 0)",
						today.minusDays(back), admin);
			}
		}
	}

	private void window(UUID point, LocalDate startDay, String start, LocalDate endDay, String end, String state) {
		UUID id = UUID.randomUUID();
		Instant from = startDay.atTime(java.time.LocalTime.parse(start)).atZone(ZONE).toInstant();
		Instant to = end == null ? null : endDay.atTime(java.time.LocalTime.parse(end)).atZone(ZONE).toInstant();
		Integer minutes = to == null ? null : (int) Duration.between(from, to).toMinutes();
		String reasons = "REVIEW".equals(state) ? "[\"LONG_WINDOW\"]" : "[]";
		Timestamp fromStamp = Timestamp.from(from);
		Timestamp toStamp = to == null ? null : Timestamp.from(to);
		jdbc.update("""
				INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
				    review_reasons, source, current_revision, created_at, updated_at, version)
				VALUES (?, ?, 'QA008 Punto', ?, ?, ?, ?, ?::jsonb, 'MANUAL', 1, now(), now(), 0)
				""", id, point, fromStamp, toStamp, minutes, state, reasons);
		jdbc.update("""
				INSERT INTO itm_window_revision (id, window_id, revision, start_at, end_at, duration_minutes, state,
				    review_reasons, change, actor_id, at)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?::jsonb, 'CREATED', ?, now())
				""", UUID.randomUUID(), id, fromStamp, toStamp, minutes, state, reasons, UUID.randomUUID());
	}

	/** Un CSV operativo sintetico para probar la importacion desde el navegador, con sus casos de la plantilla. */
	private void writeOperationalFiles() throws Exception {
		LocalDate day = LocalDate.now(ZONE).minusDays(1);
		List<Entry> rows = new ArrayList<>();
		rows.add(new Entry(2, day, null, Feature008Fixtures.BLOCKED_CODE, "QA008 Punto Bloqueado", "Intermitencia",
				List.of(Pair.of("11:00", "11:20"), Pair.of("15:00", "15:30")), "0:50"));
		rows.add(new Entry(3, day, null, Feature008Fixtures.FREE_CODE, "QA008 Punto Libre", "Intermitencia",
				List.of(Pair.of("12:00", "12:30"), Pair.of("12:00", "12:30")), "1:00"));
		rows.add(new Entry(4, day, null, "999999", "Punto desconocido", "Intermitencia", List.of(Pair.of("13:00", "13:10")),
				"0:10"));
		Files.write(Path.of("target/feature008-operational.csv"),
				SyntheticOperationalLog.csv(rows, ',', Layout.WITH_HEADER));
	}
}
