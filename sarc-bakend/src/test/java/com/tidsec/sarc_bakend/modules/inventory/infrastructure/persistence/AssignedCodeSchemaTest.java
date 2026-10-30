package com.tidsec.sarc_bakend.modules.inventory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AssignedCodeSchemaTest {

	@Test
	void migratesExistingDataAndEnforcesConditionalCodeAndGlobalUniqueness() throws Exception {
		try (var postgres = new PostgreSQLContainer("postgres:18")) {
			postgres.start();
			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
					.target("7").load().migrate();
			try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
					postgres.getPassword())) {
				UUID previous = insert(connection, "KIA", "CHARGING_POINT", "BLOCKED", "654321");
				try (var statement = connection.prepareStatement("""
						insert into asset_connector(id,equipment_id,ordinal,connector_type_id,active,created_at,updated_at,version)
						values(?,?,1,'00000000-0000-0000-0000-000000000c01',true,now(),now(),0)
						""")) {
					statement.setObject(1, UUID.randomUUID());
					statement.setObject(2, previous);
					statement.executeUpdate();
				}
				String relatedBefore = connectorSnapshot(connection, previous);
				String before = snapshot(connection, previous);
				Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
						.load().migrate();
				assertThat(snapshot(connection, previous)).isEqualTo(before);
				assertThat(connectorSnapshot(connection, previous)).isEqualTo(relatedBefore);
				insert(connection, "KIA", "CHARGING_POINT", "FREE", null);
				insert(connection, "KIA", "CHARGING_POINT", "FREE", null);
				for (String usage : new String[] { "BLOCKED", "DISCONNECTED", null }) {
					assertThatThrownBy(() -> insert(connection, "KIA", "CHARGING_POINT", usage, null))
							.isInstanceOf(SQLException.class);
				}
				for (String code : new String[] { "", " ", "012345", "12345", "1234567", "12A456" }) {
					assertThatThrownBy(() -> insert(connection, "KIA", "CHARGING_POINT", "FREE", code))
							.isInstanceOf(SQLException.class);
				}
				for (String network : new String[] { "JAC", "CONECTZ" }) {
					assertThatThrownBy(() -> insert(connection, network, "CHARGING_POINT", "FREE", null))
							.isInstanceOf(SQLException.class);
				}
				assertThatThrownBy(() -> insert(connection, "KIA", "CHARGING_STATION", null, null))
						.isInstanceOf(SQLException.class);
				for (int status : new int[] { 1, 2, 0 }) {
					try (var update = connection.prepareStatement("update asset_equipment set admin_status=? where id=?")) {
						update.setInt(1, status);
						update.setObject(2, previous);
						update.executeUpdate();
					}
					assertThatThrownBy(() -> insert(connection, "KIA", "CHARGING_POINT", "FREE", "654321"))
							.isInstanceOf(SQLException.class);
				}
			}
		}
	}

	private static String connectorSnapshot(Connection connection, UUID id) throws SQLException {
		try (var statement = connection.prepareStatement(
				"select jsonb_agg(to_jsonb(c))::text from asset_connector c where equipment_id=?")) {
			statement.setObject(1, id);
			try (var result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				return result.getString(1);
			}
		}
	}

	private static UUID insert(Connection connection, String network, String type, String usage, String code)
			throws SQLException {
		UUID id = UUID.randomUUID();
		try (var statement = connection.prepareStatement("""
				insert into asset_equipment(id,network,infrastructure_type,brand,name,serial,assigned_code,
				uid_puk,admin_status,usage_status,schedule_mode,created_at,updated_at,version)
				values(?,?,?,'WALLBOX','Prueba de esquema',?,?,?,1,?,'NOT_DEFINED',now(),now(),0)
				""")) {
			statement.setObject(1, id);
			statement.setString(2, network);
			statement.setString(3, type);
			statement.setString(4, id.toString());
			statement.setString(5, code);
			statement.setString(6, "UID-" + id);
			statement.setString(7, usage);
			statement.executeUpdate();
		}
		return id;
	}

	private static String snapshot(Connection connection, UUID id) throws SQLException {
		try (var statement = connection.prepareStatement("select row_to_json(e)::text from asset_equipment e where id=?")) {
			statement.setObject(1, id);
			try (var result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				return result.getString(1);
			}
		}
	}
}
