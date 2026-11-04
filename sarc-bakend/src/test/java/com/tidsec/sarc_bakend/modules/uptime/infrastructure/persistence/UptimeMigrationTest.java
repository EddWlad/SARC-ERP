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
		assertThat(constraints("upt_measurement")).contains("uq_upt_measurement_identity",
				"ck_upt_measurement_period", "ck_upt_measurement_effective");
		assertThat(constraints("upt_connector_alias")).contains("uq_upt_connector_alias");
		assertThat(indexes()).contains("ix_upt_measurement_connector_end", "ix_upt_measurement_period_end",
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
