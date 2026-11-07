package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Esquema de V013 sobre PostgreSQL 18: lo que la base garantiza por si sola. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class UptimeMigrationTest {

	private static final String BATCH = """
			INSERT INTO upt_import_batch (id, source, origin, checksum, original_ref, status,
			    declared_period_type, created_at, updated_at, version)
			VALUES (?, 'ENERLINK', 'PASTE', 'abc', 'ref', 'READY', ?, now(), now(), 0)
			""";

	private static final String ROW = """
			INSERT INTO upt_import_row (id, batch_id, row_number, classification, issues, observations,
			    approved, outcome, version)
			VALUES (?, ?, ?, ?, '[]', '[]', FALSE, ?, 0)
			""";

	private static final String EXCLUSION = """
			INSERT INTO upt_measurement_exclusion (id, measurement_id, action, justification,
			    measurement_version, decided_at)
			VALUES (?, ?, ?, ?, ?, now())
			""";

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void appliesV013OnTopOfPreviousMigrations() {
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '013' AND success",
				Integer.class)).isEqualTo(1);
		assertThat(tables()).contains("upt_measurement", "upt_measurement_revision",
				"upt_measurement_exclusion", "upt_import_batch", "upt_import_row", "upt_connector_alias");
	}

	@Test
	void declaresIdentityAndNamedIndexes() {
		assertThat(constraints("upt_measurement")).contains(
				"ck_upt_measurement_period", "ck_upt_measurement_effective");
		assertThat(constraints("upt_connector_alias")).contains("uq_upt_connector_alias");
		assertThat(indexes()).contains("uq_upt_measurement_identity", "ix_upt_measurement_connector_end", "ix_upt_measurement_period_end",
				"ix_upt_import_row_pending");
		assertThat(jdbc.queryForObject("""
				SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_upt_import_row_pending'
				""", String.class)).contains("WHERE");
	}

	@Test
	void referencesInventoryWithForeignKeys() {
		assertThat(foreignTargets("upt_measurement")).contains("asset_connector", "asset_equipment");
		assertThat(foreignTargets("upt_connector_alias")).contains("asset_connector", "asset_equipment");
		assertThat(foreignTargets("upt_measurement_revision")).contains("upt_measurement", "upt_import_batch");
	}

	@Test
	void revisionColumnsMatchTheirSources() {
		assertThat(length("upt_measurement_revision", "equipment_label"))
				.isEqualTo(length("asset_equipment", "name"));
		assertThat(length("upt_measurement_revision", "equipment_code"))
				.isEqualTo(length("asset_equipment", "assigned_code"));
		assertThat(length("upt_measurement_revision", "connector_type_label"))
				.isEqualTo(length("asset_connector_type", "name"));
		assertThat(length("upt_measurement_revision", "justification")).isEqualTo(500);
		assertThat(length("upt_connector_alias", "label_original")).isEqualTo(150);
	}

	@Test
	void reportedPercentHoldsTwelveDigitsAndFourDecimals() {
		assertThat(jdbc.queryForObject("""
				SELECT numeric_precision || ',' || numeric_scale FROM information_schema.columns
				WHERE table_name = 'upt_measurement_revision' AND column_name = 'reported_percent'
				""", String.class)).isEqualTo("12,4");
	}

	@Test
	void minutesAreNullableIntegers() {
		assertThat(jdbc.queryForList("""
				SELECT data_type || ':' || is_nullable FROM information_schema.columns
				WHERE table_name = 'upt_measurement_revision' AND column_name LIKE '%\\_minutes'
				""", String.class)).hasSize(4).containsOnly("integer:YES");
	}

	@Test
	void batchRejectsExactWeekAsDeclaredType() {
		assertRejected(BATCH, UUID.randomUUID(), "EXACT_WEEK");
		jdbc.update(BATCH, UUID.randomUUID(), "ROLLING_7D");
	}

	@Test
	void rowsAreUniqueInsideBatchAndUseClosedVocabulary() {
		UUID batch = UUID.randomUUID();
		jdbc.update(BATCH, batch, "ROLLING_24H");
		jdbc.update(ROW, UUID.randomUUID(), batch, 1, "NEW", null);

		assertRejected(ROW, UUID.randomUUID(), batch, 1, "NEW", null);
		assertRejected(ROW, UUID.randomUUID(), batch, 2, "DUPLICATE", null);
		assertRejected(ROW, UUID.randomUUID(), batch, 3, "NEW", "DONE");
		jdbc.update(ROW, UUID.randomUUID(), batch, 2, "PENDING_LINK", "PENDING_LINK");
	}

	@Test
	void exclusionsRequireKnownActionAndJustification() {
		assertThat(constraints("upt_measurement_exclusion")).contains("uq_upt_measurement_exclusion",
				"ck_upt_exclusion_action", "ck_upt_exclusion_justification");
		assertRejected(EXCLUSION, UUID.randomUUID(), UUID.randomUUID(), "DELETED", "x", 1L);
	}

	@Test
	void revisionChecksCoverSpecialClassExactWeekAndValidation() {
		assertThat(constraints("upt_measurement_revision")).contains("uq_upt_measurement_revision",
				"ck_upt_revision_class", "ck_upt_revision_period_type", "ck_upt_revision_state",
				"ck_upt_revision_origin", "ck_upt_revision_minutes", "ck_upt_revision_special",
				"ck_upt_revision_exact", "ck_upt_revision_validated", "ck_upt_revision_observations");
	}

    @Test
    void temporalPrecisionUsesConditionalBoundsAndLocalDurableWork() {
        assertThat(tables()).contains("upt_measurement_temporal_change", "upt_temporal_transition_work");
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_name='upt_measurement' AND column_name='period_start'",String.class)).isEqualTo("YES");
        assertThat(indexes()).contains("uq_upt_measurement_reference");
        assertThat(constraints("upt_measurement")).contains("ck_upt_measurement_precision","ck_upt_measurement_temporal");
        assertThat(constraints("upt_temporal_transition_work")).contains("uq_upt_temporal_work_request","ck_upt_temporal_work_status","ck_upt_temporal_work_items");
    }

    @Test
    void upgradeFromV015PreservesRevisionsExclusionAndArchivedPublication() {
        try (var db = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18")) {
            db.start();
            var oldFlyway = org.flywaydb.core.Flyway.configure()
                .dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                .locations("classpath:db/migration").target("015").load();
            oldFlyway.migrate();
            var legacy = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                db.getJdbcUrl(),db.getUsername(),db.getPassword()));
            com.tidsec.sarc_bakend.support.Feature005Fixtures.seedInventory(legacy);
            UUID batch=UUID.randomUUID(), measurement=UUID.randomUUID(), report=UUID.randomUUID();
            UUID connector=com.tidsec.sarc_bakend.support.Feature006Fixtures.CONNECTOR_1;
            UUID equipment=com.tidsec.sarc_bakend.support.Feature005Fixtures.JAC_STATION;
            legacy.update("""
                INSERT INTO upt_import_batch(id,source,origin,checksum,original_ref,status,declared_period_type,
                    declared_period_start,declared_period_end,created_at,updated_at,version)
                VALUES(?,'ENERLINK','FILE','original-sha','qa006/original','COMPLETED','ROLLING_7D',
                    '2026-10-19T05:00:00Z','2026-10-26T05:00:00Z',now(),now(),3)
                """,batch);
            legacy.update("""
                INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_start,period_end,
                    period_zone,latest_revision,effective_revision,excluded,created_at,updated_at,version)
                VALUES(?,?,?,'ENERLINK','2026-10-19T05:00:00Z','2026-10-26T05:00:00Z',
                    'America/Guayaquil',2,1,true,now(),now(),7)
                """,measurement,connector,equipment);
            for (int revision=1;revision<=2;revision++) {
                legacy.update("""
                    INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,
                        available_minutes,occupied_minutes,fault_minutes,offline_minutes,reported_percent,
                        review_state,observations,origin,import_batch_id,source_values,equipment_label,
                        connector_type_label,exact_confirmed_at,entered_at,validated_at,uptime_basis)
                    VALUES(?,?,?,'MEASURED','EXACT_WEEK',9815,null,0,234,97.6800,?,'[]',
                        'FILE',?,'{"availableHours":"163.5833"}','Nombre historico','Tipo2',
                        now(),now(),CASE WHEN ?=1 THEN now() END,'REPORTED_PERCENT')
                    """,UUID.randomUUID(),measurement,revision,revision==1?"VALIDATED":"PENDING",batch,revision);
            }
            legacy.update("""
                INSERT INTO report_issued(id,kind,format,file_name,scope,checksum,size_bytes,file_ref,
                    created_at,updated_at,version,content_profile,cut_off,criteria)
                VALUES(?,'UPTIME','PDF','anterior.pdf','historico','hash-antes',42,'qa006/archivado',
                    now(),now(),0,'OFFICIAL_ONLY',now(),'{"from":"2026-10-19","to":"2026-10-26"}')
                """,report);
            var before = legacy.queryForList("SELECT row_to_json(x)::text FROM upt_measurement_revision x ORDER BY revision",String.class);
            String archiveBefore=legacy.queryForObject("SELECT row_to_json(x)::text FROM report_issued x WHERE id=?",String.class,report);
            org.flywaydb.core.Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
                .locations("classpath:db/migration").load().migrate();
            assertThat(legacy.queryForObject("SELECT period_precision FROM upt_measurement WHERE id=?",String.class,measurement))
                .isEqualTo("REQUIRES_RECONCILIATION");
            assertThat(legacy.queryForObject("SELECT reference_week FROM upt_measurement WHERE id=?",java.time.LocalDate.class,measurement)).isNull();
            assertThat(legacy.queryForMap("SELECT latest_revision,effective_revision,excluded,version FROM upt_measurement WHERE id=?",measurement))
                .containsEntry("latest_revision",2).containsEntry("effective_revision",1).containsEntry("excluded",true).containsEntry("version",7L);
            assertThat(legacy.queryForList("SELECT row_to_json(x)::text FROM upt_measurement_revision x ORDER BY revision",String.class)).isEqualTo(before);
            assertThat(legacy.queryForObject("SELECT row_to_json(x)::text FROM report_issued x WHERE id=?",String.class,report)).isEqualTo(archiveBefore);
            // Candidatos fuera de claves canonicas, sin elegir ni borrar un ganador.
            legacy.update("""
                INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_start,period_end,
                    period_zone,latest_revision,created_at,updated_at,version,period_precision,
                    reference_week,reference_period_type)
                VALUES(?,?,?,'ENERLINK',null,null,'America/Guayaquil',1,now(),now(),0,
                    'REFERENCE_WEEK','2026-10-19','ROLLING_7D')
                """,UUID.randomUUID(),connector,equipment);
            assertThatThrownBy(()->legacy.update("""
                INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_start,period_end,
                    period_zone,latest_revision,created_at,updated_at,version,period_precision,
                    reference_week,reference_period_type)
                VALUES(?,?,?,'ENERLINK',null,null,'America/Guayaquil',1,now(),now(),0,
                    'REFERENCE_WEEK','2026-10-19','ROLLING_7D')
                """,UUID.randomUUID(),connector,equipment)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->legacy.update("UPDATE upt_measurement SET period_start=now() WHERE period_precision='REFERENCE_WEEK'"))
                .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(()->legacy.update("UPDATE upt_measurement SET reference_week='2026-10-20' WHERE period_precision='REFERENCE_WEEK'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

	private void assertRejected(String sql, Object... args) {
		assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private List<String> tables() {
		return jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
	}

	private List<String> indexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
	}

	private List<String> constraints(String table) {
		return jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass", String.class,
				table);
	}

	private List<String> foreignTargets(String table) {
		return jdbc.queryForList("""
				SELECT confrelid::regclass::text FROM pg_constraint
				WHERE conrelid = ?::regclass AND contype = 'f'
				""", String.class, table);
	}

	private Integer length(String table, String column) {
		return jdbc.queryForObject("""
				SELECT character_maximum_length FROM information_schema.columns
				WHERE table_name = ? AND column_name = ?
				""", Integer.class, table, column);
	}
}
