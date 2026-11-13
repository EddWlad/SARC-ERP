package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Esquema de V018 sobre PostgreSQL 18: lo que la base garantiza por si sola. */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class IntermittencySchemaTest {

	private static final String WINDOW = """
			INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
			    review_reasons, source, current_revision, created_at, updated_at, version)
			VALUES (?, ?, 'QA008 Punto', ?::timestamptz, ?::timestamptz, ?, ?, '[]', 'MANUAL', 1, now(), now(), 0)
			""";

	private static final String COLUMNS = """
			SELECT table_name || '.' || column_name || ':' || data_type || ':' || is_nullable || ':'
			    || coalesce(character_maximum_length::text, '')
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name NOT LIKE 'itm\\_%'
			ORDER BY table_name, column_name
			""";

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clean() {
		jdbc.execute("TRUNCATE itm_reviewed_day_event, itm_reviewed_day, itm_window_revision, itm_import_row,"
				+ " itm_window, itm_import_batch CASCADE");
	}

	@Test
	void appliesV018OnTopOfV017AndCreatesBtreeGist() {
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '018' AND success", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'",
				Integer.class)).isEqualTo(1);
		assertThat(tables()).contains("itm_window", "itm_window_revision", "itm_import_batch", "itm_import_row",
				"itm_reviewed_day", "itm_reviewed_day_event");
	}

	@Test
	void rejectsOverlapBetweenNonAnnulledStates() {
		UUID point = UUID.randomUUID();
		insert(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");

		assertRejected(point, "2026-10-15T10:30:00-05:00", "2026-10-15T10:45:00-05:00", 15, "REVIEW");
		assertRejected(point, "2026-10-15T10:59:00-05:00", "2026-10-15T11:30:00-05:00", 31, "TEST");
		// Una ventana que empieza justo cuando termina la otra no se solapa: los rangos son [inicio, fin).
		insert(point, "2026-10-15T11:00:00-05:00", "2026-10-15T11:30:00-05:00", 30, "CONFIRMED");
		// Otro punto con las mismas horas no se ve afectado.
		insert(UUID.randomUUID(), "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");
	}

	@Test
	void anOpenWindowOccupiesFromItsStartUntilItCloses() {
		UUID point = UUID.randomUUID();
		insert(point, "2026-10-15T16:01:00-05:00", null, null, "OPEN");

		assertRejected(point, "2026-10-15T18:00:00-05:00", "2026-10-15T18:30:00-05:00", 30, "CONFIRMED");
		assertRejected(point, "2026-10-20T09:00:00-05:00", "2026-10-20T09:10:00-05:00", 10, "CONFIRMED");
		// Antes de su inicio queda libre.
		insert(point, "2026-10-15T09:00:00-05:00", "2026-10-15T09:30:00-05:00", 30, "CONFIRMED");
		// Dos abiertas del mismo punto tampoco caben.
		assertRejected(point, "2026-10-16T08:00:00-05:00", null, null, "OPEN");
	}

	@Test
	void anAnnulledWindowDoesNotBlockNorCollide() {
		UUID point = UUID.randomUUID();
		insert(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "ANNULLED");

		insert(point, "2026-10-15T10:30:00-05:00", "2026-10-15T10:45:00-05:00", 15, "CONFIRMED");
		insert(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "ANNULLED");
		// Una ventana abierta que se anula puede quedar sin fin.
		insert(point, "2026-10-16T10:00:00-05:00", null, null, "ANNULLED");
	}

	@Test
	void rejectsIdenticalWindowsWithTheirOwnIndex() {
		UUID point = UUID.randomUUID();
		insert(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");

		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");
		assertThat(indexes()).contains("uq_itm_window_identity");
		assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_itm_window_identity'",
				String.class)).contains("WHERE");
		assertThat(constraints("itm_window")).contains("ex_itm_window_overlap");
	}

	@Test
	void enforcesOpenEndMinuteAndDurationRules() {
		UUID point = UUID.randomUUID();
		// OPEN con fin, y fin sin OPEN.
		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "OPEN");
		assertRejected(point, "2026-10-15T10:00:00-05:00", null, null, "CONFIRMED");
		// Segundos distintos de cero.
		assertRejected(point, "2026-10-15T10:00:30-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");
		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:15-05:00", 60, "CONFIRMED");
		// Duracion cero, negativa o que no coincide con los instantes.
		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T10:00:00-05:00", 0, "CONFIRMED");
		assertRejected(point, "2026-10-15T11:00:00-05:00", "2026-10-15T10:00:00-05:00", -60, "CONFIRMED");
		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 59, "CONFIRMED");
		// Vocabulario cerrado.
		assertRejected(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "PENDING_LINK");
		insert(point, "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");
	}

	@Test
	void revisionsRequireJustificationExceptWhenCreatedOrClosed() {
		UUID window = UUID.randomUUID();
		insert(window, UUID.randomUUID(), "2026-10-15T10:00:00-05:00", "2026-10-15T11:00:00-05:00", 60, "CONFIRMED");
		String revision = """
				INSERT INTO itm_window_revision (id, window_id, revision, start_at, end_at, duration_minutes, state,
				    review_reasons, change, justification, actor_id, at)
				VALUES (?, ?, ?, '2026-10-15T10:00:00-05:00', '2026-10-15T11:00:00-05:00', 60, 'CONFIRMED', '[]', ?, ?, ?, now())
				""";

		jdbc.update(revision, UUID.randomUUID(), window, 1, "CREATED", null, UUID.randomUUID());
		jdbc.update(revision, UUID.randomUUID(), window, 3, "CLOSED", null, UUID.randomUUID());
		assertThatThrownBy(() -> jdbc.update(revision, UUID.randomUUID(), window, 2, "CORRECTED", null,
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(revision, UUID.randomUUID(), window, 2, "CORRECTED", "  ",
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(revision, UUID.randomUUID(), window, 2, "DELETED", "x",
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update(revision, UUID.randomUUID(), window, 2, "CORRECTED", "Dato corregido", UUID.randomUUID());
		// La misma revision no se repite.
		assertThatThrownBy(() -> jdbc.update(revision, UUID.randomUUID(), window, 2, "CORRECTED", "Otra",
				UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void importRowsAreUniquePerBatchAndUseClosedVocabulary() {
		UUID batch = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO itm_import_batch (id, origin, checksum, original_ref, status, format, created_at,
				    updated_at, version)
				VALUES (?, 'FILE', 'abc', 'ref', 'READY', 'XLSX', now(), now(), 0)
				""", batch);
		String row = """
				INSERT INTO itm_import_row (id, batch_id, row_number, raw, classification, decision, skip_reason, version)
				VALUES (?, ?, ?, '{}', ?, ?, ?, 0)
				""";

		jdbc.update(row, UUID.randomUUID(), batch, 1, "NEW", null, null);
		assertThatThrownBy(() -> jdbc.update(row, UUID.randomUUID(), batch, 1, "NEW", null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(row, UUID.randomUUID(), batch, 2, "DUPLICATE", null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(row, UUID.randomUUID(), batch, 3, "NEW", "DONE", null))
				.isInstanceOf(DataIntegrityViolationException.class);
		// Omitir exige motivo.
		assertThatThrownBy(() -> jdbc.update(row, UUID.randomUUID(), batch, 4, "NEW", "SKIP", null))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update(row, UUID.randomUUID(), batch, 4, "NEW", "SKIP", "Duplicada en el archivo");
		jdbc.update(row, UUID.randomUUID(), batch, 5, "PENDING_LINK", "KEEP_PENDING", null);
		assertThat(indexes()).contains("ix_itm_import_row_pending", "ix_itm_import_row_touched_days");
	}

	@Test
	void reviewedDayEventsRequireJustificationWhenWithdrawn() {
		jdbc.update("INSERT INTO itm_reviewed_day (day, active, marked_by, marked_at, version)"
				+ " VALUES ('2026-10-14', TRUE, ?, now(), 0)", UUID.randomUUID());
		String event = """
				INSERT INTO itm_reviewed_day_event (id, day, action, justification, actor_id, at)
				VALUES (?, '2026-10-14', ?, ?, ?, now())
				""";

		jdbc.update(event, UUID.randomUUID(), "MARKED", null, UUID.randomUUID());
		assertThatThrownBy(() -> jdbc.update(event, UUID.randomUUID(), "WITHDRAWN", null, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
		jdbc.update(event, UUID.randomUUID(), "WITHDRAWN", "Se encontro un dato nuevo", UUID.randomUUID());
	}

	@Test
	void upgradeFromV017LeavesEveryExistingTableUnchanged() {
		try (var db = new org.testcontainers.postgresql.PostgreSQLContainer("postgres:18")) {
			db.start();
			Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
					.locations("classpath:db/migration").target("017").load().migrate();
			JdbcTemplate legacy = new JdbcTemplate(
					new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
			List<String> before = legacy.queryForList(COLUMNS, String.class);
			assertThat(before).anyMatch(column -> column.startsWith("upt_measurement."))
					.anyMatch(column -> column.startsWith("energy_session.")).anyMatch(column -> column.startsWith("asset_equipment."));

			Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
					.locations("classpath:db/migration").load().migrate();

			assertThat(legacy.queryForList(COLUMNS, String.class)).isEqualTo(before);
			assertThat(legacy.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
					+ " AND table_name LIKE 'itm\\_%'", String.class)).hasSize(6);
		}
	}

	private void insert(UUID point, String start, String end, Integer minutes, String state) {
		insert(UUID.randomUUID(), point, start, end, minutes, state);
	}

	private void insert(UUID id, UUID point, String start, String end, Integer minutes, String state) {
		jdbc.update(WINDOW, id, point, start, end, minutes, state);
	}

	private void assertRejected(UUID point, String start, String end, Integer minutes, String state) {
		assertThatThrownBy(() -> insert(point, start, end, minutes, state))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private List<String> tables() {
		return jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);
	}

	private List<String> indexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
	}

	private List<String> constraints(String table) {
		return jdbc.queryForList("SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass", String.class,
				table);
	}
}
