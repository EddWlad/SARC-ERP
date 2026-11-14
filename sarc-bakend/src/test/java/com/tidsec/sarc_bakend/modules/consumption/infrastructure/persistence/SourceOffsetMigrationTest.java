package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Feature 009 (hotfix H002): V019 guarda el desplazamiento de origen y recalcula la fecha de atribucion de
 * las sesiones existentes sin tocar sus instantes. Los datos se siembran por JDBC en el esquema V018.
 */
class SourceOffsetMigrationTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
	/** Lote de produccion cuyo archivo declara "INICIO (UTC+00:00)" (listado en V019 con su evidencia). */
	private static final UUID BATCH_ENERLINK_UTC = UUID.fromString("2f5f0384-82e5-43c3-812f-1839e969eb1c");
	/** Cualquier otro lote Enerlink: su archivo declara UTC-05:00. */
	private static final UUID BATCH_ENERLINK_ECUADOR = UUID.randomUUID();
	private static final UUID BATCH_WALLBOX = UUID.randomUUID();

	@BeforeAll
	static void migrateWithLegacyData() throws SQLException {
		POSTGRES.start();
		flyway(MigrationVersion.fromVersion("18")).migrate();
		try (Connection connection = connection()) {
			batch(connection, BATCH_ENERLINK_UTC, "ENERLINK");
			batch(connection, BATCH_ENERLINK_ECUADOR, "ENERLINK");
			batch(connection, BATCH_WALLBOX, "WALLBOX");
			// Fechas tal como las calculaba la version anterior: siempre en America/Guayaquil.
			session(connection, "ENERLINK", "1212", "2026-11-11T01:36:15Z", "2026-11-10", BATCH_ENERLINK_UTC);
			session(connection, "ENERLINK", "1300", "2026-11-11T15:00:00Z", "2026-11-11", BATCH_ENERLINK_UTC);
			session(connection, "ENERLINK", "900", "2026-11-11T01:36:15Z", "2026-11-10", BATCH_ENERLINK_ECUADOR);
			session(connection, "WALLBOX", "S-9", "2026-11-11T04:59:11Z", "2026-11-10", BATCH_WALLBOX);
			row(connection, BATCH_ENERLINK_UTC, 1, "2026-11-12T02:00:00Z");
			row(connection, BATCH_ENERLINK_ECUADOR, 1, "2026-11-12T02:00:00Z");
		}
		flyway(null).migrate();
	}

	@AfterAll
	static void stop() {
		POSTGRES.stop();
	}

	@Test
	void onlySessionsFromFilesDeclaringUtcMoveToTheirSourceDate() throws SQLException {
		try (Connection connection = connection()) {
			assertThat(dateOf(connection, "1212")).isEqualTo(LocalDate.of(2026, 11, 11));
			assertThat(dateOf(connection, "1300")).isEqualTo(LocalDate.of(2026, 11, 11));
			assertThat(offsetOf(connection, "1212")).isZero();
			assertThat(startedAtOf(connection, "1212")).isEqualTo(Instant.parse("2026-11-11T01:36:15Z"));
		}
	}

	@Test
	void filesDeclaringEcuadorTimeAndWallboxKeepTheirDate() throws SQLException {
		try (Connection connection = connection()) {
			assertThat(dateOf(connection, "900")).isEqualTo(LocalDate.of(2026, 11, 10));
			assertThat(offsetOf(connection, "900")).isEqualTo(-300);
			assertThat(dateOf(connection, "S-9")).isEqualTo(LocalDate.of(2026, 11, 10));
			assertThat(offsetOf(connection, "S-9")).isEqualTo(-300);
		}
	}

	@Test
	void importRowsTakeTheOffsetDeclaredByTheirBatch() throws SQLException {
		try (Connection connection = connection()) {
			assertThat(rowOffsetOf(connection, BATCH_ENERLINK_UTC)).isZero();
			assertThat(rowOffsetOf(connection, BATCH_ENERLINK_ECUADOR)).isEqualTo(-300);
		}
	}

	private static int rowOffsetOf(Connection connection, UUID batch) throws SQLException {
		try (PreparedStatement statement = connection
				.prepareStatement("SELECT source_utc_offset_minutes FROM energy_import_row WHERE batch_id = ?")) {
			statement.setObject(1, batch);
			try (ResultSet rs = statement.executeQuery()) {
				assertThat(rs.next()).isTrue();
				return rs.getInt(1);
			}
		}
	}

	private static Flyway flyway(MigrationVersion target) {
		var configuration = Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.locations("classpath:db/migration");
		if (target != null) {
			configuration.target(target);
		}
		return configuration.load();
	}

	private static Connection connection() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static void batch(Connection connection, UUID id, String source) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO energy_import_batch (id, source, source_scope, origin, checksum, original_ref, status,
				    created_at, updated_at, version)
				VALUES (?, ?, '', 'FILE', 'x', 'sarc/imports/original', 'COMPLETED', now(), now(), 0)
				""")) {
			statement.setObject(1, id);
			statement.setString(2, source);
			statement.executeUpdate();
		}
	}

	private static void session(Connection connection, String source, String externalId, String startedAt,
			String startDate, UUID batch) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO energy_session (id, source, source_scope, external_session_id, started_at, ended_at,
				    start_date, energy_kwh, state, revision, import_batch_id, source_approved, created_at,
				    updated_at, version)
				VALUES (?, ?, '', ?, ?, ?, ?, 1, 'PENDING_LINK', 1, ?, false, now(), now(), 0)
				""")) {
			Timestamp start = Timestamp.from(Instant.parse(startedAt));
			statement.setObject(1, UUID.randomUUID());
			statement.setString(2, source);
			statement.setString(3, externalId);
			statement.setTimestamp(4, start);
			statement.setTimestamp(5, start);
			statement.setObject(6, LocalDate.parse(startDate));
			statement.setObject(7, batch);
			statement.executeUpdate();
		}
	}

	private static void row(Connection connection, UUID batch, int number, String startedAt) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO energy_import_row (id, batch_id, row_number, started_at, classification, issues,
				    non_preferred_source, test, approved, reassigned)
				VALUES (?, ?, ?, ?, 'NEW', '[]'::jsonb, false, false, false, false)
				""")) {
			statement.setObject(1, UUID.randomUUID());
			statement.setObject(2, batch);
			statement.setInt(3, number);
			statement.setTimestamp(4, Timestamp.from(Instant.parse(startedAt)));
			statement.executeUpdate();
		}
	}

	private static LocalDate dateOf(Connection connection, String externalId) throws SQLException {
		return value(connection, "start_date", externalId).getObject(1, LocalDate.class);
	}

	private static int offsetOf(Connection connection, String externalId) throws SQLException {
		return value(connection, "source_utc_offset_minutes", externalId).getInt(1);
	}

	private static Instant startedAtOf(Connection connection, String externalId) throws SQLException {
		return value(connection, "started_at", externalId).getTimestamp(1).toInstant();
	}

	private static ResultSet value(Connection connection, String column, String externalId) throws SQLException {
		PreparedStatement statement = connection
				.prepareStatement("SELECT " + column + " FROM energy_session WHERE external_session_id = ?");
		statement.setString(1, externalId);
		ResultSet rs = statement.executeQuery();
		assertThat(rs.next()).isTrue();
		return rs;
	}
}
