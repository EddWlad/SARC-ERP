package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class EnergySessionSchemaTest {

	@Test
	void migratesExistingSessionsIntactAndOnlyAdmitsAbsenceOfEndAndEnergy() throws Exception {
		try (var postgres = new PostgreSQLContainer("postgres:18")) {
			postgres.start();
			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.target("8").load().migrate();
			try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword())) {
				UUID batch = insertBatch(connection);
				UUID previous = insertSession(connection, batch, "S-1", "now() + interval '1 hour'", "12.500", null);
				String before = snapshot(connection, previous);

				Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
						.load().migrate();

				assertThat(snapshot(connection, previous)).isEqualTo(before);
				insertSession(connection, batch, "S-2", "null", "null", "IDENTITY_AMBIGUOUS");
				assertThatThrownBy(() -> insertSession(connection, batch, "S-3", "now() - interval '1 hour'", "1", null))
						.isInstanceOf(SQLException.class).hasMessageContaining("ck_energy_session_span");
				assertThatThrownBy(() -> insertSession(connection, batch, "S-4", "null", "-1", null))
						.isInstanceOf(SQLException.class).hasMessageContaining("ck_energy_session_energy");
				assertThatThrownBy(() -> insertSession(connection, batch, "S-5", "null", "null", "OTRO"))
						.isInstanceOf(SQLException.class).hasMessageContaining("ck_energy_session_link_issue");
			}
		}
	}

	private static UUID insertBatch(Connection connection) throws SQLException {
		UUID id = UUID.randomUUID();
		try (var statement = connection.prepareStatement("""
				insert into energy_import_batch(id,source,source_scope,origin,checksum,original_ref,status,created_at,
				updated_at,version) values(?,'ENERLINK','','FILE','x','ref','COMPLETED',now(),now(),0)
				""")) {
			statement.setObject(1, id);
			statement.executeUpdate();
		}
		return id;
	}

	private static UUID insertSession(Connection connection, UUID batch, String externalId, String end, String energy,
			String linkIssue) throws SQLException {
		UUID id = UUID.randomUUID();
		String issueColumn = linkIssue == null ? "" : ",link_issue";
		String issueValue = linkIssue == null ? "" : ",'" + linkIssue + "'";
		try (var statement = connection.prepareStatement("""
				insert into energy_session(id,source,source_scope,external_session_id,started_at,ended_at,start_date,
				energy_kwh,state,revision,import_batch_id,created_at,updated_at,version,source_approved%s)
				values(?,'ENERLINK','',?,now(),%s,current_date,%s,'PENDING_LINK',1,?,now(),now(),0,false%s)
				""".formatted(issueColumn, end, energy, issueValue))) {
			statement.setObject(1, id);
			statement.setString(2, externalId);
			statement.setObject(3, batch);
			statement.executeUpdate();
		}
		return id;
	}

	private static String snapshot(Connection connection, UUID id) throws SQLException {
		try (var statement = connection.prepareStatement("""
				select external_session_id||'|'||energy_kwh||'|'||ended_at::text||'|'||state||'|'||revision
				from energy_session where id=?
				""")) {
			statement.setObject(1, id);
			try (var result = statement.executeQuery()) {
				result.next();
				return result.getString(1);
			}
		}
	}
}
