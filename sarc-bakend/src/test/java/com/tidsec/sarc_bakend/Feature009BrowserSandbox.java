package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Email;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.identity.domain.model.UserAccount;
import com.tidsec.sarc_bakend.modules.identity.domain.repository.UserRepository;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * QA opt-in de Feature 009 (T033 y T034): base desechable de Testcontainers con usuarios por rol y sesiones del mes en
 * curso (una Enerlink con fin en otro desplazamiento, una sin energia y una Wallbox) para recorrer Ver, Editar y
 * Eliminar y reproducir el refresco del tablero. No toca la base de desarrollo ni la de compose. Se ejecuta con
 * {@code -Dsarc.qa.browser009=true}; el recorrido crea {@code target/feature009-browser.done} al terminar.
 */
@EnabledIfSystemProperty(named = "sarc.qa.browser009", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"sarc.security.bcrypt-strength=4", "server.address=127.0.0.1", "spring.docker.compose.enabled=false" })
@Import(TestcontainersConfiguration.class)
class Feature009BrowserSandbox {

	static final String PASSWORD = "Prueba-Feature009-2026";

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

	@LocalServerPort
	int port;

	@Autowired
	UserRepository users;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EnergySessionRepository sessions;

	@Autowired
	PlatformTransactionManager transactions;

	@MockitoBean
	FileStorage storage;

	@Test
	void browserAcceptanceAgainstDisposableDatabase() throws Exception {
		for (Role role : new Role[] { Role.ADMINISTRADOR, Role.ANALISTA, Role.GERENTE }) {
			UserAccount user = UserAccount.create(new Email(role.name().toLowerCase() + "@qa009.example.test"),
					"QA009 " + role, Set.of(role), encoder.encode(PASSWORD));
			user.changePassword(encoder.encode(PASSWORD));
			users.save(user);
		}
		when(storage.storeDocument(anyString(), anyString(), any()))
				.thenAnswer(invocation -> "sandbox/009/" + UUID.randomUUID());
		seedSessions();

		Path done = Path.of("target/feature009-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(Path.of("target/feature009-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature009-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
		}
		assertThat(Files.exists(done)).as("Recorrido QA009 finalizado").isTrue();
	}

	private void seedSessions() {
		UUID batch = UUID.randomUUID();
		jdbc.update("""
				insert into energy_import_batch(id,source,source_scope,origin,checksum,original_ref,status,created_at,updated_at,version)
				values(?,'ENERLINK','','FILE','synthetic','sandbox/009','COMPLETED',now(),now(),0)
				""", batch);
		EquipmentReference first = equipment(0);
		EquipmentReference second = equipment(1);
		LocalDate today = LocalDate.now(ZONE);
		LocalDate monthStart = today.withDayOfMonth(1);
		for (LocalDate day = monthStart; day.isBefore(today); day = day.plusDays(1)) {
			int index = day.getDayOfMonth();
			Instant start = day.atTime(9, 15).atZone(ZONE).toInstant();
			save(SessionSource.ENERLINK, "QA-" + index, new SessionReading(start, start.plusSeconds(3_725),
					BigDecimal.valueOf(5 + index % 7), "QA", "C0", null, null, ZoneOffset.ofHours(-5)),
					index % 2 == 0 ? first : second, batch);
		}
		Instant utcStart = today.atStartOfDay(ZoneOffset.UTC).plusMinutes(30).toInstant();
		save(SessionSource.ENERLINK, "1211", new SessionReading(utcStart, utcStart.plusSeconds(6_005),
				BigDecimal.valueOf(8), "QA", "C0", null, null, ZoneOffset.UTC, ZoneOffset.ofHours(-5)), first, batch);
		Instant unknown = today.atTime(7, 0).atZone(ZONE).toInstant();
		save(SessionSource.ENERLINK, "SIN-ENERGIA", new SessionReading(unknown, null, null, "QA", "C1", null, null,
				ZoneOffset.ofHours(-5)), second, batch);
		Instant wallbox = today.atTime(6, 0).atZone(ZONE).toInstant();
		save(SessionSource.WALLBOX, "W-77", new SessionReading(wallbox, wallbox.plusSeconds(1_800),
				BigDecimal.valueOf(4.5), null, null, "QA-1", null, ZoneOffset.ofHours(-5)), second, batch);
	}

	private EquipmentReference equipment(int index) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into asset_equipment(id,network,infrastructure_type,brand,name,serial,assigned_code,
				admin_status,schedule_mode,created_at,updated_at,version)
				values(?,'JAC','CHARGING_STATION','BENNY',?,?,?,1,'NOT_DEFINED',now(),now(),0)
				""", id, "QA009 Equipo " + (index + 1), "QA9-" + index, "QA9-" + index);
		jdbc.update("""
				insert into asset_external_identity(id,equipment_id,platform,scope,external_id,verified,verified_at,created_at,updated_at,version)
				values(gen_random_uuid(),?,'ENERLINK','QA',?,true,now(),now(),now(),0)
				""", id, "C" + index);
		return new EquipmentReference(id, ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION,
				"QA009 Equipo " + (index + 1), "QA9-" + index);
	}

	private void save(SessionSource source, String externalId, SessionReading reading, EquipmentReference equipment,
			UUID batch) {
		new TransactionTemplate(transactions).executeWithoutResult(status -> sessions.save(EnergySession.record(source,
				"", externalId, reading, equipment, true, false, batch, new OperatingZone(ZONE))));
	}
}
