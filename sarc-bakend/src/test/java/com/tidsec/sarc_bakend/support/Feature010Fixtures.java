package com.tidsec.sarc_bakend.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;

/**
 * Semilla de la feature 010: equipos sinteticos con codigos ficticios {@code 9xxxxx} y numeracion de prueba. Solo se
 * invoca desde pruebas con PostgreSQL desechable.
 *
 * <p>El ambiente de numeracion de las pruebas es {@code TEST}: lo aporta {@code application-dev.yaml}, el perfil por
 * defecto, y no hace falta fijarlo aqui. Una prueba que necesite la propiedad vacia la anula con
 * {@code @TestPropertySource(properties = "sarc.incidents.numbering.environment=")}.
 *
 * <p>El numero inicial de produccion (P-01) no esta en ningun archivo: aqui solo hay numeros ficticios.
 */
public final class Feature010Fixtures {

	public static final UUID TYPE2 = UUID.fromString("00000000-0000-0000-0000-000000000c01");
	public static final UUID CCS2 = UUID.fromString("00000000-0000-0000-0000-000000000c02");

	/** Numero inicial ficticio de pruebas, lejos de cualquier secuencia real. */
	public static final int TEST_START_NUMBER = 9001;

	public static final String SITE_NAME = "CC Prueba Norte";
	public static final String SITE_MAPS_URL = "https://maps.example.test/cc-prueba-norte";
	public static final String SITE_WITHOUT_EQUIPMENT = "Sitio sin equipos";

	public static final String JAC_POINT_CODE = "910001";
	public static final String KIA_STATION_CODE = "910002";
	public static final String NO_MAPS_CODE = "910003";
	public static final String SITE_REFERENCE_CODE = "910004";
	public static final String INACTIVE_CODE = "910005";
	public static final String RETIRED_CONNECTOR_CODE = "910006";
	public static final String DELETED_CODE = "910007";

	/** Punto de carga JAC de un conector, con ubicacion y enlace de Maps. */
	public static final UUID JAC_POINT = equipmentId("jac-point");
	/** Electrolinera KIA de dos conectores, con ubicacion y enlace de Maps. */
	public static final UUID KIA_STATION = equipmentId("kia-station");
	/** Equipo sin ubicacion ni enlace de Maps: el mensaje se copia igual. */
	public static final UUID NO_MAPS = equipmentId("no-maps");
	/** Equipo CONECTZ en {@link #SITE_NAME}, para usarlo como equipo de referencia de un elemento del sitio. */
	public static final UUID SITE_REFERENCE = equipmentId("site-reference");
	public static final UUID INACTIVE = equipmentId("inactive");
	/** Electrolinera con el conector 2 retirado (inactivo). */
	public static final UUID RETIRED_CONNECTOR = equipmentId("retired-connector");
	public static final UUID DELETED = equipmentId("deleted");

	public static final String PASSWORD = "Prueba-Incidencias-2026";

	/** Cada rol por separado, un segundo Analista y la combinacion Analista mas Gerente (union de permisos). */
	public static final List<Set<Role>> ROLE_SETS = List.of(Set.of(Role.ADMINISTRADOR), Set.of(Role.ANALISTA),
			Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
			Set.of(Role.ANALISTA, Role.GERENTE));

	public static final String SECOND_ANALYST_EMAIL = "analista-2@qa010.example.test";

	private Feature010Fixtures() {
	}

	/** Correo de un usuario de prueba para ese conjunto de roles. */
	public static String emailFor(Set<Role> roles) {
		return roles.stream().map(role -> role.name().toLowerCase(Locale.ROOT)).sorted()
				.collect(Collectors.joining("-")) + "@qa010.example.test";
	}

	public static void seedInventory(JdbcTemplate jdbc) {
		equipment(jdbc, JAC_POINT, "JAC", "CHARGING_POINT", "WALLBOX", "QA010 JAC Punto", 1, JAC_POINT_CODE,
				"Via Prueba 1", "https://maps.example.test/jac-punto");
		connector(jdbc, JAC_POINT, 1, TYPE2, true);

		equipment(jdbc, KIA_STATION, "KIA", "CHARGING_STATION", "BENNY", "QA010 KIA Electrolinera", 1,
				KIA_STATION_CODE, "Via Prueba 2", "https://maps.example.test/kia-electrolinera");
		connector(jdbc, KIA_STATION, 1, CCS2, true);
		connector(jdbc, KIA_STATION, 2, TYPE2, true);

		equipment(jdbc, NO_MAPS, "JAC", "CHARGING_STATION", "BENNY", "QA010 Sin Maps", 1, NO_MAPS_CODE, null, null);
		connector(jdbc, NO_MAPS, 1, TYPE2, true);

		equipment(jdbc, SITE_REFERENCE, "CONECTZ", "CHARGING_STATION", "BENNY", "QA010 CONECTZ Referencia", 1,
				SITE_REFERENCE_CODE, SITE_NAME, SITE_MAPS_URL);
		connector(jdbc, SITE_REFERENCE, 1, CCS2, true);
		connector(jdbc, SITE_REFERENCE, 2, CCS2, true);

		equipment(jdbc, INACTIVE, "JAC", "CHARGING_STATION", "BENNY", "QA010 Inactivo", 2, INACTIVE_CODE, null,
				null);
		connector(jdbc, INACTIVE, 1, CCS2, true);

		equipment(jdbc, RETIRED_CONNECTOR, "KIA", "CHARGING_STATION", "BENNY", "QA010 Conector Retirado", 1,
				RETIRED_CONNECTOR_CODE, "Via Prueba 3", "https://maps.example.test/retirado");
		connector(jdbc, RETIRED_CONNECTOR, 1, CCS2, true);
		connector(jdbc, RETIRED_CONNECTOR, 2, CCS2, false);

		equipment(jdbc, DELETED, "JAC", "CHARGING_STATION", "BENNY", "QA010 Eliminado", 0, DELETED_CODE, null,
				null);
		connector(jdbc, DELETED, 1, CCS2, true);
	}

	/** Numeracion configurada en {@code TEST} desde {@link #TEST_START_NUMBER}. */
	public static void configureNumbering(JdbcTemplate jdbc) {
		configureNumbering(jdbc, TEST_START_NUMBER, "TEST");
	}

	public static void configureNumbering(JdbcTemplate jdbc, int start, String environment) {
		jdbc.update("""
				UPDATE inc_ticket_numbering
				   SET start_number = ?, next_number = ?, environment = ?, configured_at = now()
				 WHERE id = 1
				""", start, start, environment);
	}

	/** Numeracion sin configurar: en cualquier ambiente no se pueden crear tickets. */
	public static void clearNumbering(JdbcTemplate jdbc) {
		jdbc.update("""
				UPDATE inc_ticket_numbering
				   SET start_number = NULL, next_number = NULL, environment = NULL, configured_at = NULL,
				       configured_by = NULL
				 WHERE id = 1
				""");
	}

	/** Borra los datos de incidencias, en el orden que respeta sus claves foraneas. Deja intactas la numeracion y las referencias. */
	public static void clearIncidents(JdbcTemplate jdbc) {
		for (String table : List.of("inc_evidence", "inc_communication", "inc_visit", "inc_event",
				"inc_milestone_revision", "inc_milestone", "inc_episode", "inc_ticket_relation",
				"inc_ticket_connector", "inc_ticket")) {
			jdbc.update("DELETE FROM " + table);
		}
	}

	public static UUID connectorId(UUID equipment, int ordinal) {
		return UUID.nameUUIDFromBytes((equipment + "#" + ordinal).getBytes(StandardCharsets.UTF_8));
	}

	private static UUID equipmentId(String key) {
		return UUID.nameUUIDFromBytes(("QA010-" + key).getBytes(StandardCharsets.UTF_8));
	}

	private static void equipment(JdbcTemplate jdbc, UUID id, String network, String type, String brand, String name,
			int adminStatus, String code, String location, String mapsUrl) {
		jdbc.update("""
				INSERT INTO asset_equipment (id, network, infrastructure_type, brand, name, serial, assigned_code,
				    uid_puk, admin_status, usage_status, schedule_mode, location, maps_url, created_at, updated_at,
				    version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_DEFINED', ?, ?, now(), now(), 0)
				""", id, network, type, brand, name, code, code,
				"CHARGING_POINT".equals(type) ? "UID-" + code : null, adminStatus,
				"CHARGING_POINT".equals(type) ? "FREE" : null, location, mapsUrl);
	}

	private static void connector(JdbcTemplate jdbc, UUID equipment, int ordinal, UUID type, boolean active) {
		jdbc.update("""
				INSERT INTO asset_connector (id, equipment_id, ordinal, connector_type_id, active, created_at,
				    updated_at, version)
				VALUES (?, ?, ?, ?, ?, now(), now(), 0)
				""", connectorId(equipment, ordinal), equipment, ordinal, type, active);
	}
}
