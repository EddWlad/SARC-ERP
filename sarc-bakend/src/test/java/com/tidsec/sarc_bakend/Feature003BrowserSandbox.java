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
import java.time.ZoneId;
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

/** QA opt-in con PostgreSQL desechable, puerto aleatorio y almacenamiento simulado. */
@EnabledIfSystemProperty(named = "sarc.qa.browser", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "sarc.security.bcrypt-strength=4", "server.address=127.0.0.1" })
@Import(TestcontainersConfiguration.class)
class Feature003BrowserSandbox {
	@LocalServerPort int port;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Autowired JdbcTemplate jdbc;
	@Autowired EnergySessionRepository sessions;
	@Autowired PlatformTransactionManager transactions;
	@MockitoBean FileStorage storage;

	@Test
	void browserAcceptanceAgainstDisposableDatabase() throws Exception {
		for (Role role : new Role[] { Role.ANALISTA, Role.GERENTE }) {
			String password = "Prueba-Feature003-2026";
			UserAccount user = UserAccount.create(new Email(role.name().toLowerCase() + "@qa003.example.test"),
					"QA 003 " + role, Set.of(role), encoder.encode(password));
			user.changePassword(encoder.encode(password));
			users.save(user);
		}
		when(storage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> "sandbox/003/" + UUID.randomUUID());
		UUID batch = UUID.randomUUID();
		jdbc.update("""
				insert into energy_import_batch(id,source,source_scope,origin,checksum,original_ref,status,created_at,updated_at,version)
				values(?,'ENERLINK','','FILE','synthetic','sandbox/003','COMPLETED',now(),now(),0)
				""", batch);
		int[] energy = {100, 90, 80, 70, 70, 70, 60};
		for (int i = 0; i < energy.length; i++) {
			UUID equipment = UUID.randomUUID();
			jdbc.update("""
					insert into asset_equipment(id,network,infrastructure_type,brand,name,serial,assigned_code,
					admin_status,schedule_mode,created_at,updated_at,version)
					values(?,'JAC','CHARGING_STATION','BENNY',?,?,?,1,'NOT_DEFINED',now(),now(),0)
					""", equipment, "QA Equipo " + (i + 1), "QA-" + i, "QA-" + i);
			jdbc.update("""
					insert into asset_external_identity(id,equipment_id,platform,scope,external_id,verified,verified_at,created_at,updated_at,version)
					values(gen_random_uuid(),?,'ENERLINK','QA',?,true,now(),now(),now(),0)
					""", equipment, "C" + i);
			EquipmentReference reference = new EquipmentReference(equipment, ChargingNetwork.JAC,
					InfrastructureType.CHARGING_STATION, "QA Equipo " + (i + 1), "QA-" + i);
			SessionReading reading = new SessionReading(Instant.parse("2026-10-21T13:00:00Z"), null,
					BigDecimal.valueOf(energy[i]), "QA", "C" + i, null, null);
			int index = i;
			new TransactionTemplate(transactions).executeWithoutResult(status -> sessions.save(EnergySession.record(
					SessionSource.ENERLINK, "", "RANK-" + index, reading, reference, false, false, batch,
					new OperatingZone(ZoneId.of("America/Guayaquil")))));
		}
		String header = "ID\tINICIO (UTC-05:00)\tTÉRMINO (UTC-05:00)\tACTIVO\tCARGADOR\tCONECTOR\tENERGIA CARGADA (kWh)\n";
		Files.writeString(Path.of("target/feature003-enerlink.csv"), header + "QA-HIGH\t21-10-2026 08:00:00\t\tQA\tC0\tCCS2\t1500\n");
		Files.writeString(Path.of("target/feature003-wallbox.csv"),
				"Serial number\tCharger\tSession id\tStart\tEnd\tEnergy (kWh)\n"
				+ "QA-0\tQA\tQA-WALLBOX\t2026-10-21 08:00:00\t2026-10-21 09:00:00\t1500,000\n");
		Path done = Path.of("target/feature003-browser.done");
		Files.deleteIfExists(done);
		Files.writeString(Path.of("target/feature003-browser-proxy.json"),
				"{\"/api\":{\"target\":\"http://127.0.0.1:" + port + "\",\"secure\":false}}");
		Files.writeString(Path.of("target/feature003-browser.ready"), Integer.toString(port));
		Instant deadline = Instant.now().plus(Duration.ofMinutes(30));
		while (!Files.exists(done) && Instant.now().isBefore(deadline)) Thread.sleep(500);
		assertThat(Files.exists(done)).as("Recorrido de navegador finalizado").isTrue();
		assertThat(jdbc.queryForObject("select count(*) from energy_session_review", Integer.class)).isGreaterThanOrEqualTo(2);
	}
}
