package com.tidsec.sarc_bakend.support;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;

/**
 * Semilla de la feature 008: puntos de carga KIA sinteticos con codigos ficticios {@code 9xxxxx}. Solo se
 * invoca desde pruebas con PostgreSQL desechable.
 *
 * <p>{@code AMBIGUOUS_CODE} no se puede sembrar aqui: V004 impone {@code UNIQUE (assigned_code)} y V008 no
 * lo retira, de modo que dos equipos no comparten codigo. Esa clasificacion se prueba con un
 * {@code KiaPointDirectory} simulado que devuelve dos puntos para un mismo codigo.
 */
public final class Feature008Fixtures {

	public static final String BLOCKED_CODE = "900001";
	public static final String FREE_CODE = "900002";
	public static final String DISCONNECTED_CODE = "900003";
	public static final String INACTIVE_CODE = "900004";
	public static final String DELETED_CODE = "900005";
	public static final String TEST_CODE = "900006";
	/** Punto activo con cero conflictos: base de las pruebas de captura. */
	public static final String SECOND_BLOCKED_CODE = "900007";
	/** Codigo con formato valido que ningun equipo tiene. */
	public static final String UNKNOWN_CODE = "999999";

	public static final UUID BLOCKED = equipmentId("blocked");
	public static final UUID SECOND_BLOCKED = equipmentId("blocked-2");
	public static final UUID FREE = equipmentId("free");
	public static final UUID FREE_WITHOUT_CODE = equipmentId("free-without-code");
	public static final UUID DISCONNECTED = equipmentId("disconnected");
	public static final UUID INACTIVE = equipmentId("inactive");
	public static final UUID DELETED = equipmentId("deleted");
	public static final UUID KIA_STATION = equipmentId("kia-station");
	public static final UUID JAC_POINT = equipmentId("jac-point");
	public static final UUID TEST_POINT = equipmentId("test-point");

	/** Nombre de cada punto KIA activo con codigo, para que los archivos sinteticos lo repitan sin diferencias. */
	public static final java.util.Map<String, String> POINT_NAMES = java.util.Map.of(
			BLOCKED_CODE, "QA008 Punto Bloqueado",
			SECOND_BLOCKED_CODE, "QA008 Punto Bloqueado Dos",
			FREE_CODE, "QA008 Punto Libre",
			DISCONNECTED_CODE, "QA008 Punto Desconectado",
			TEST_CODE, "PRUEBAS CDA");

	public static final String PASSWORD ="Prueba-Intermitencias-2026";

	/** Cada rol por separado y las combinaciones que prueban la union de permisos (FR-007). */
	public static final List<Set<Role>> ROLE_SETS = List.of(Set.of(Role.ADMINISTRADOR), Set.of(Role.ANALISTA),
			Set.of(Role.GERENTE), Set.of(Role.SUPERVISOR), Set.of(Role.ASISTENTE),
			Set.of(Role.ANALISTA, Role.GERENTE), Set.of(Role.ADMINISTRADOR, Role.ASISTENTE),
			Set.of(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE));

	private Feature008Fixtures() {
	}

	/** Correo de un usuario de prueba para ese conjunto de roles. */
	public static String emailFor(Set<Role> roles) {
		return roles.stream().map(role -> role.name().toLowerCase(java.util.Locale.ROOT)).sorted()
				.collect(Collectors.joining("-")) + "@qa008.example.test";
	}

	public static void seedInventory(JdbcTemplate jdbc) {
		point(jdbc, BLOCKED, "QA008 Punto Bloqueado", BLOCKED_CODE, "BLOCKED", 1);
		point(jdbc, SECOND_BLOCKED, "QA008 Punto Bloqueado Dos", SECOND_BLOCKED_CODE, "BLOCKED", 1);
		point(jdbc, FREE, "QA008 Punto Libre", FREE_CODE, "FREE", 1);
		point(jdbc, FREE_WITHOUT_CODE, "QA008 Punto Libre Sin Codigo", null, "FREE", 1);
		point(jdbc, DISCONNECTED, "QA008 Punto Desconectado", DISCONNECTED_CODE, "DISCONNECTED", 1);
		point(jdbc, INACTIVE, "QA008 Punto Inactivo", INACTIVE_CODE, "BLOCKED", 2);
		point(jdbc, DELETED, "QA008 Punto Eliminado", DELETED_CODE, "BLOCKED", 0);
		point(jdbc, TEST_POINT, "PRUEBAS CDA", TEST_CODE, "BLOCKED", 1);
		// Fuera de alcance: una electrolinera KIA y un punto de otra red. Solo los puntos KIA separan codigo y serie.
		other(jdbc, KIA_STATION, "KIA", "CHARGING_STATION", "QA008 KIA Electrolinera", "qa008-kia-station", null);
		other(jdbc, JAC_POINT, "JAC", "CHARGING_POINT", "QA008 JAC Punto", "qa008-jac-point", "FREE");
	}

	/** Ids de los puntos KIA activos, en orden estable. */
	public static List<UUID> activeKiaPoints() {
		return List.of(BLOCKED, SECOND_BLOCKED, FREE, FREE_WITHOUT_CODE, DISCONNECTED, TEST_POINT).stream()
				.sorted(Comparator.comparing(UUID::toString)).toList();
	}

	private static UUID equipmentId(String key) {
		return UUID.nameUUIDFromBytes(("QA008-" + key).getBytes(StandardCharsets.UTF_8));
	}

	private static void point(JdbcTemplate jdbc, UUID id, String name, String code, String usage, int adminStatus) {
		String key = code == null ? id.toString() : code;
		jdbc.update("""
				INSERT INTO asset_equipment (id, network, infrastructure_type, brand, name, serial, assigned_code,
				    uid_puk, admin_status, usage_status, schedule_mode, created_at, updated_at, version)
				VALUES (?, 'KIA', 'CHARGING_POINT', 'BENNY', ?, ?, ?, ?, ?, ?, 'NOT_DEFINED', now() - interval '90 days', now(), 0)
				""", id, name, "QA008-" + key, code, "UID-QA008-" + key, adminStatus, usage);
	}

	private static void other(JdbcTemplate jdbc, UUID id, String network, String type, String name, String serial,
			String usage) {
		jdbc.update("""
				INSERT INTO asset_equipment (id, network, infrastructure_type, brand, name, serial, assigned_code,
				    uid_puk, admin_status, usage_status, schedule_mode, created_at, updated_at, version)
				VALUES (?, ?, ?, 'BENNY', ?, ?, ?, ?, 1, ?, 'NOT_DEFINED', now(), now(), 0)
				""", id, network, type, name, serial, serial, "CHARGING_POINT".equals(type) ? "UID-" + serial : null,
				usage);
	}
}
