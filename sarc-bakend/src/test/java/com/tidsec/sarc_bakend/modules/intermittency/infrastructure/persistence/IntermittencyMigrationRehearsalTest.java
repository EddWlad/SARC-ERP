package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Ensayo de V018 y de la restauracion con datos de intermitencias, sobre un PostgreSQL 18 desechable y con el rol
 * que usa la aplicacion, que es propietario de su base pero no superusuario (T006, T127 en su parte de base de
 * datos). La base de compose y cualquier base persistente quedan fuera. Lo que este ensayo no cubre y sigue
 * pendiente: restaurar la ultima copia cifrada de produccion, que necesita la copia y su clave, y los originales
 * importados en Cloudinary.
 */
class IntermittencyMigrationRehearsalTest {

	private static final String APP = "sarc_app";
	private static final String PASSWORD = "ensayo-" + System.nanoTime();
	private static PostgreSQLContainer postgres;

	@BeforeAll
	static void start() throws Exception {
		postgres = new PostgreSQLContainer("postgres:18");
		postgres.start();
		try (Connection admin = connect(postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());
				Statement statement = admin.createStatement()) {
			statement.execute("CREATE ROLE " + APP + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '" + PASSWORD + "'");
			statement.execute("CREATE DATABASE rehearsal OWNER " + APP);
			statement.execute("CREATE DATABASE restored OWNER " + APP);
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	private static Connection connect(String user, String password, String database) throws Exception {
		return DriverManager.getConnection(postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1"), user,
				password);
	}

	private static Flyway flyway(String target) {
		return Flyway.configure().dataSource(postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/rehearsal$1"), APP, PASSWORD)
				.locations("classpath:db/migration").target(target).load();
	}

	private static Map<String, Long> counts(String database) throws Exception {
		Map<String, Long> counts = new TreeMap<>();
		try (Connection connection = connect(APP, PASSWORD, database); Statement statement = connection.createStatement()) {
			List<String> tables = new ArrayList<>();
			try (ResultSet names = statement.executeQuery(
					"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
				while (names.next()) {
					tables.add(names.getString(1));
				}
			}
			for (String table : tables) {
				try (ResultSet count = statement.executeQuery("SELECT count(*) FROM \"" + table + "\"")) {
					count.next();
					counts.put(table, count.getLong(1));
				}
			}
		}
		return counts;
	}

	private static List<String> existingColumns() throws Exception {
		List<String> columns = new ArrayList<>();
		try (Connection connection = connect(APP, PASSWORD, "rehearsal"); Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("""
						SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable
						FROM information_schema.columns
						WHERE table_schema = 'public' AND table_name NOT LIKE 'itm\\_%' AND table_name <> 'flyway_schema_history'
						ORDER BY table_name, column_name
						""")) {
			while (rows.next()) {
				columns.add(rows.getString(1));
			}
		}
		return columns;
	}

	@Test
	void v018AppliesWithTheApplicationRoleInUnderFiveSecondsAndLeavesExistingTablesAlone() throws Exception {
		assertThat(flyway("017").migrate().migrationsExecuted).isPositive();
		List<String> before = existingColumns();
		long started = System.nanoTime();

		var result = flyway("018").migrate();

		long millis = (System.nanoTime() - started) / 1_000_000;
		assertThat(result.migrationsExecuted).isEqualTo(1);
		assertThat(millis).as("V018 con el rol de la aplicacion").isLessThan(5_000);
		assertThat(existingColumns()).as("las tablas existentes no cambian").isEqualTo(before);
		try (Connection connection = connect(APP, PASSWORD, "rehearsal"); Statement statement = connection.createStatement()) {
			try (ResultSet extension = statement.executeQuery(
					"SELECT extname FROM pg_extension WHERE extname = 'btree_gist'")) {
				assertThat(extension.next()).as("btree_gist creada por un rol que no es superusuario").isTrue();
			}
			try (ResultSet owner = statement.executeQuery("SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
				owner.next();
				assertThat(owner.getBoolean(1)).isFalse();
			}
			statement.execute("""
					INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
					    review_reasons, source, current_revision, created_at, updated_at, version)
					VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Punto', '2026-10-15T10:00:00-05',
					    '2026-10-15T11:00:00-05', 60, 'CONFIRMED', '[]', 'MANUAL', 1, now(), now(), 0)
					""");
			// La restriccion de exclusion funciona con ese rol: un solapamiento se rechaza.
			assertThatThrownBy(() -> statement.execute("""
					INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
					    review_reasons, source, current_revision, created_at, updated_at, version)
					VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Punto', '2026-10-15T10:30:00-05',
					    '2026-10-15T10:45:00-05', 15, 'REVIEW', '[]', 'MANUAL', 1, now(), now(), 0)
					""")).hasMessageContaining("itm_window");
		}
	}

	@Test
	void aDumpWithIntermittencyDataRestoresWithTheSameCountsPerTable() throws Exception {
		flyway("018").migrate();
		try (Connection connection = connect(APP, PASSWORD, "rehearsal"); Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE itm_reviewed_day_event, itm_reviewed_day, itm_window_revision, itm_window CASCADE");
			statement.execute("""
					INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
					    review_reasons, source, current_revision, created_at, updated_at, version)
					SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000002', 'Punto',
					    timestamptz '2026-01-01 00:00-05' + (n || ' hours')::interval * 6,
					    timestamptz '2026-01-01 00:00-05' + (n || ' hours')::interval * 6 + interval '30 minutes', 30,
					    'CONFIRMED', '[]', 'MANUAL', 1, now(), now(), 0
					FROM generate_series(0, 199) AS n
					""");
			statement.execute("INSERT INTO itm_reviewed_day (day, active, marked_by, marked_at, version) "
					+ "SELECT d::date, true, gen_random_uuid(), now(), 0 FROM generate_series('2026-10-01'::date, '2026-10-10'::date, '1 day') d");
			statement.execute("INSERT INTO itm_reviewed_day_event (id, day, action, actor_id, at, pending_batches_warned) "
					+ "SELECT gen_random_uuid(), d::date, 'MARKED', gen_random_uuid(), now(), 0 FROM generate_series('2026-10-01'::date, '2026-10-10'::date, '1 day') d");
		}
		Map<String, Long> original = counts("rehearsal");

		var dump = postgres.execInContainer("sh", "-c",
				"PGPASSWORD='" + PASSWORD + "' pg_dump -h localhost -U " + APP + " -Fc -f /tmp/rehearsal.dump rehearsal");
		assertThat(dump.getExitCode()).as(dump.getStderr()).isZero();
		var restore = postgres.execInContainer("sh", "-c",
				"PGPASSWORD='" + PASSWORD + "' pg_restore -h localhost -U " + APP + " -d restored /tmp/rehearsal.dump");
		assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();

		Map<String, Long> restored = counts("restored");
		assertThat(restored).isEqualTo(original);
		assertThat(restored.get("itm_window")).isEqualTo(200);
		assertThat(restored.get("itm_reviewed_day")).isEqualTo(10);
		assertThat(restored.get("itm_reviewed_day_event")).isEqualTo(10);
		try (Connection connection = connect(APP, PASSWORD, "restored"); Statement statement = connection.createStatement();
				ResultSet version = statement.executeQuery(
						"SELECT max(version::int) FROM flyway_schema_history WHERE success")) {
			version.next();
			assertThat(version.getInt(1)).as("ultima migracion restaurada").isEqualTo(18);
		}
	}
}
