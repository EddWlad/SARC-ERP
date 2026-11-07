package com.tidsec.sarc_bakend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prueba de actualizacion (Feature 007, FR-020): un esquema en la version anterior a la ultima,
 * con datos sinteticos, se migra a la ultima y la aplicacion arranca con
 * {@code spring.jpa.hibernate.ddl-auto=validate} conservando los datos.
 *
 * <p>Los datos del estado anterior se insertan por JDBC (no con las fixtures de {@code support/}):
 * esas fixtures usan los repositorios JPA, que solo funcionan con el esquema final. Un contenedor
 * propio y desechable aísla la prueba; nunca toca la base local de desarrollo.
 */
@SpringBootTest
class MigrationUpgradeTest {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
	private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000007");
	private static final String USER_EMAIL = "actualizacion@example.invalid";
	private static final int TOTAL_MIGRATIONS;
	private static final String PENULTIMATE;

	static {
		POSTGRES.start();
		Flyway all = flyway(null);
		MigrationInfo[] migrations = all.info().all();
		TOTAL_MIGRATIONS = migrations.length;
		assertThat(TOTAL_MIGRATIONS).as("se necesitan al menos dos migraciones").isGreaterThanOrEqualTo(2);
		MigrationVersion penultimate = migrations[migrations.length - 2].getVersion();
		PENULTIMATE = penultimate.getVersion();
		flyway(penultimate).migrate();
		seedSyntheticData();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired JdbcTemplate jdbc;

	@Test
	void upgradesFromThePreviousVersionKeepingData() {
		// Si el contexto arranco, Flyway aplico lo pendiente y Hibernate valido el esquema final.
		Integer applied = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
		Integer failed = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
		assertThat(applied).as("migraciones aplicadas (partia de la %s)", PENULTIMATE).isEqualTo(TOTAL_MIGRATIONS);
		assertThat(failed).isZero();

		Integer users = jdbc.queryForObject("SELECT count(*) FROM auth_user WHERE email = ?", Integer.class, USER_EMAIL);
		assertThat(users).as("el usuario sintetico sigue presente").isEqualTo(1);
		Integer roles = jdbc.queryForObject(
				"SELECT count(*) FROM auth_user_role WHERE user_id = ? AND role_code = 'ANALISTA'", Integer.class,
				USER_ID);
		assertThat(roles).as("su rol sigue presente").isEqualTo(1);
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

	private static void seedSyntheticData() {
		try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword())) {
			try (PreparedStatement user = connection.prepareStatement("""
					INSERT INTO auth_user (id, email, full_name, password_hash, status, must_change_password,
					    failed_login_attempts, created_at, updated_at, version)
					VALUES (?, ?, 'Usuario sintetico de actualizacion', 'hash-sintetico', 'ACTIVE', false,
					    0, now(), now(), 0)
					""")) {
				user.setObject(1, USER_ID);
				user.setString(2, USER_EMAIL);
				user.executeUpdate();
			}
			try (PreparedStatement role = connection
					.prepareStatement("INSERT INTO auth_user_role (user_id, role_code) VALUES (?, 'ANALISTA')")) {
				role.setObject(1, USER_ID);
				role.executeUpdate();
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("No se pudieron sembrar los datos sinteticos de la prueba", e);
		}
	}
}
