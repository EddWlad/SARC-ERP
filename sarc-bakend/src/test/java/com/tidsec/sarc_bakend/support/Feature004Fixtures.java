package com.tidsec.sarc_bakend.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Semilla QA aislada. Solo se invoca desde tests con PostgreSQL desechable. */
public final class Feature004Fixtures {
	public static final UUID BATCH = UUID.fromString("40000000-0000-0000-0000-000000000001");
	public static final UUID EQUIPMENT = UUID.fromString("40000000-0000-0000-0000-000000000002");

	private Feature004Fixtures() {}

	public static void seed(JdbcTemplate jdbc, boolean load) {
		jdbc.update(
				"""
                insert into energy_import_batch(id,source,source_scope,origin,checksum,original_ref,status,created_at,updated_at,version)
                values(?,'ENERLINK','QA004','FILE','synthetic-qa004','sandbox/004','COMPLETED',now(),now(),0)
                """,
				BATCH);
		for (int i = 0; i < 7; i++) {
			UUID eq = equipment(i);
			jdbc.update(
					"""
                    insert into asset_equipment(id,network,infrastructure_type,brand,name,serial,assigned_code,admin_status,schedule_mode,created_at,updated_at,version)
                    values(?,'JAC','CHARGING_STATION','BENNY',?,?,?,1,'NOT_DEFINED',now(),now(),0)
                    """,
					eq,
					"QA004 Equipo "
							+ (i + 1)
							+ " con nombre extenso para comprobar etiquetas y empates",
					"QA004-" + i,
					"QA004-" + i);
			int energy = new int[] {100, 90, 80, 70, 70, 70, 60}[i];
			insert(jdbc, UUID.randomUUID(), eq, "RANK-" + i, "VALIDATED", null, (double) energy);
		}
		insert(jdbc, UUID.randomUUID(), EQUIPMENT, "MISSING", "VALIDATED", null, null);
		insert(jdbc, UUID.randomUUID(), EQUIPMENT, "ZERO", "VALIDATED", null, 0d);
		insert(jdbc, UUID.randomUUID(), null, "PENDING_LINK", "PENDING_LINK", null, 35d);
		insert(
				jdbc,
				UUID.randomUUID(),
				EQUIPMENT,
				"AWAITING_APPROVAL",
				"AWAITING_APPROVAL",
				null,
				36d);
		insert(jdbc, UUID.randomUUID(), EQUIPMENT, "TEST", "TEST", null, 37d);
		insert(jdbc, UUID.randomUUID(), EQUIPMENT, "REVIEW", "ENERGY_REVIEW", "PENDING", 1500d);
		UUID rejected = UUID.randomUUID();
		insert(jdbc, rejected, EQUIPMENT, "REJECTED", "ENERGY_REJECTED", "REJECTED", 1600d);
		review(jdbc, rejected, 0, "REJECTED", "QA004 rechazo vigente", 1600);
		UUID approved = UUID.randomUUID();
		insert(jdbc, approved, EQUIPMENT, "APPROVED", "VALIDATED", "APPROVED", 1700d);
		review(jdbc, approved, 0, "REJECTED", "QA004 HISTORIA OPERATIVA RESTRINGIDA", 1700);
		review(jdbc, approved, 1, "APPROVED", "QA004 aprobación oficial vigente", 1700);
		if (load)
			jdbc.update(
					"""
                    insert into energy_session(id,source,source_scope,external_session_id,equipment_id,network,infrastructure_type,
                        started_at,start_date,energy_kwh,state,revision,import_batch_id,source_approved,created_at,updated_at,version)
                    select gen_random_uuid(),'ENERLINK','QA004','LOAD-'||n,?,'JAC','CHARGING_STATION',
                        timestamptz '2026-10-01 12:00:00-05' + ((n-1)%30)*interval '1 day',
                        date '2026-10-01'+((n-1)%30),1.125,'VALIDATED',1,?,false,now(),now(),0
                    from generate_series(1,250000) as n
                    """,
					EQUIPMENT, BATCH);
	}

	public static UUID equipment(int index) {
		return index == 0
				? EQUIPMENT
				: UUID.nameUUIDFromBytes(
						("QA004-EQ-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static void insert(
			JdbcTemplate jdbc,
			UUID id,
			UUID equipment,
			String key,
			String state,
			String review,
			Double energy) {
		jdbc.update(
				"""
                insert into energy_session(id,source,source_scope,external_session_id,equipment_id,network,infrastructure_type,
                    started_at,start_date,energy_kwh,state,energy_review,revision,import_batch_id,source_approved,created_at,updated_at,version)
                values(?,'ENERLINK','QA004',?,?,?,?,'2026-10-21 13:00:00+00','2026-10-21',?,?,?,1,?,false,now(),now(),0)
                """,
				id,
				key,
				equipment,
				equipment == null ? null : "JAC",
				equipment == null ? null : "CHARGING_STATION",
				energy,
				state,
				review,
				BATCH);
	}

	private static void review(
			JdbcTemplate jdbc,
			UUID session,
			int version,
			String decision,
			String reason,
			int energy) {
		jdbc.update(
				"""
                insert into energy_session_review(id,session_id,session_revision,session_version,decision,justification,energy_kwh,decided_at)
                values(gen_random_uuid(),?,1,?,?,?,?,now())
                """,
				session,
				version,
				decision,
				reason,
				energy);
	}
}
