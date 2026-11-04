package com.tidsec.sarc_bakend.support;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;

/** Semilla de la feature 005. Solo se invoca desde pruebas con PostgreSQL desechable. */
public final class Feature005Fixtures {

	public static final UUID TYPE2 = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	public static final UUID CCS2 = UUID.fromString("00000000-0000-0000-0000-000000000c02");

	public static final UUID KIA_STATION = equipmentId("kia-station");
	public static final UUID KIA_POINT = equipmentId("kia-point");
	public static final UUID JAC_STATION = equipmentId("jac-station");
	public static final UUID JAC_POINT = equipmentId("jac-point");
	public static final UUID CONECTZ_STATION = equipmentId("conectz-station");
	public static final UUID INACTIVE_EQUIPMENT = equipmentId("inactive");
	public static final UUID DELETED_EQUIPMENT = equipmentId("deleted");

	/** Cada rol por separado y la combinacion operativa con consulta. */
	public static final List<Set<Role>> ROLE_SETS = List.of(Set.of(Role.ADMINISTRADOR), Set.of(Role.ANALISTA),
			Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
			Set.of(Role.ANALISTA, Role.GERENTE));

	private Feature005Fixtures() {
	}

	/** Equipos de uno a tres conectores; la tercera posicion de JAC queda inactiva. */
	public static void seedInventory(JdbcTemplate jdbc) {
		equipment(jdbc, KIA_STATION, "KIA", "CHARGING_STATION", "BENNY", "QA005 KIA Electrolinera", 1, "kia-1");
		connector(jdbc, KIA_STATION, 1, CCS2, true);
		equipment(jdbc, KIA_POINT, "KIA", "CHARGING_POINT", "BENNY", "QA005 KIA Punto", 1, "100001");
		connector(jdbc, KIA_POINT, 1, TYPE2, true);
		equipment(jdbc, JAC_STATION, "JAC", "CHARGING_STATION", "BENNY", "QA005 JAC Estación", 1, "jac-1");
		connector(jdbc, JAC_STATION, 1, TYPE2, true);
		connector(jdbc, JAC_STATION, 2, CCS2, true);
		connector(jdbc, JAC_STATION, 3, CCS2, false);
		equipment(jdbc, JAC_POINT, "JAC", "CHARGING_POINT", "WALLBOX", "QA005 JAC Punto", 1, "jac-2");
		connector(jdbc, JAC_POINT, 1, TYPE2, true);
		equipment(jdbc, CONECTZ_STATION, "CONECTZ", "CHARGING_STATION", "BENNY", "QA005 CONECTZ Estación", 1, "cz-1");
		connector(jdbc, CONECTZ_STATION, 1, CCS2, true);
		connector(jdbc, CONECTZ_STATION, 2, CCS2, true);
		equipment(jdbc, INACTIVE_EQUIPMENT, "JAC", "CHARGING_STATION", "BENNY", "QA005 Inactivo", 2, "jac-3");
		connector(jdbc, INACTIVE_EQUIPMENT, 1, CCS2, true);
		equipment(jdbc, DELETED_EQUIPMENT, "JAC", "CHARGING_STATION", "BENNY", "QA005 Eliminado", 0, "jac-4");
		connector(jdbc, DELETED_EQUIPMENT, 1, CCS2, true);
	}

	public static UUID connectorId(UUID equipment, int ordinal) {
		return UUID.nameUUIDFromBytes((equipment + "#" + ordinal).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static UUID equipmentId(String key) {
		return UUID.nameUUIDFromBytes(("QA005-" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static void equipment(JdbcTemplate jdbc, UUID id, String network, String type, String brand, String name,
			int adminStatus, String code) {
		jdbc.update("""
				INSERT INTO asset_equipment (id, network, infrastructure_type, brand, name, serial, assigned_code,
				    uid_puk, admin_status, usage_status, schedule_mode, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_DEFINED', now(), now(), 0)
				""", id, network, type, brand, name, kiaPoint(network, type) ? "QA005-" + code : code, code,
				"CHARGING_POINT".equals(type) ? "UID-" + code : null, adminStatus,
				"CHARGING_POINT".equals(type) ? "FREE" : null);
	}

	/** Solo los puntos KIA separan codigo y serie; en el resto el esquema exige que coincidan. */
	private static boolean kiaPoint(String network, String type) {
		return "KIA".equals(network) && "CHARGING_POINT".equals(type);
	}

	private static void connector(JdbcTemplate jdbc, UUID equipment, int ordinal, UUID type, boolean active) {
		jdbc.update("""
				INSERT INTO asset_connector (id, equipment_id, ordinal, connector_type_id, active, created_at,
				    updated_at, version)
				VALUES (?, ?, ?, ?, ?, now(), now(), 0)
				""", connectorId(equipment, ordinal), equipment, ordinal, type, active);
	}
}
