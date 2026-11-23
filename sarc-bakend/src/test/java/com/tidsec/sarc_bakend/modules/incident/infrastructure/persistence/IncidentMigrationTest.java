package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Esquema de V021 sobre PostgreSQL 18: lo que la base garantiza por si sola (feature 010). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class IncidentMigrationTest {

	private static final UUID REFERENCE_VERSION = UUID.fromString("5b3f1c0e-0a10-4a01-9a10-000000000001");

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		for (String table : List.of("inc_evidence", "inc_communication", "inc_visit", "inc_event",
				"inc_milestone_revision", "inc_milestone", "inc_episode", "inc_ticket_relation",
				"inc_ticket_connector", "inc_ticket")) {
			jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void appliesV021OnTopOfV020WithoutTouchingExistingTables() throws IOException {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '021' AND success",
				Integer.class)).isEqualTo(1);
		assertThat(tables()).contains("inc_ticket_numbering", "inc_reference_version", "inc_reference_rule",
				"inc_ticket", "inc_ticket_connector", "inc_ticket_relation", "inc_episode", "inc_milestone",
				"inc_milestone_revision", "inc_event", "inc_visit", "inc_communication", "inc_evidence");

		String sql = new ClassPathResource("db/migration/V021__incidencias_tickets.sql")
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(sql).doesNotContainIgnoringCase("ALTER TABLE");
		assertThat(sql).doesNotContainIgnoringCase("DROP ");
		assertThat(sql.lines().filter(line -> line.toUpperCase().startsWith("CREATE TABLE"))
				.allMatch(line -> line.contains("CREATE TABLE inc_"))).isTrue();
	}

	@Test
	void numberingRowExistsEmptyAndNothingIsSeeded() {
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM inc_ticket_numbering");
		assertThat(((Number) row.get("id")).intValue()).isEqualTo(1);
		assertThat(row.get("start_number")).isNull();
		assertThat(row.get("next_number")).isNull();
		assertThat(row.get("environment")).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_ticket_numbering", Integer.class)).isEqualTo(1);
		assertRejected(() -> jdbc.update("INSERT INTO inc_ticket_numbering (id) VALUES (2)"));
	}

	@Test
	void v022OnlyRelabelsTheInitialReferenceVersion() throws IOException {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '022' AND success",
				Integer.class)).isEqualTo(1);
		String sql = new ClassPathResource("db/migration/V022__incidencias_rotulo_protocolo.sql")
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(sql).containsIgnoringCase("UPDATE inc_reference_version").containsIgnoringCase("SET label");
		assertThat(sql).doesNotContainIgnoringCase("inc_reference_rule").doesNotContainIgnoringCase("DELETE")
				.doesNotContainIgnoringCase("DROP ").doesNotContainIgnoringCase("ALTER TABLE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_reference_version", Integer.class)).isEqualTo(1);
	}

	@Test
	void seedsTheInitialReferenceVersionWithOneRulePerSupportedType() {
		Map<String, Object> version = jdbc.queryForMap(
				"SELECT code, label, source_note FROM inc_reference_version WHERE id = ?", REFERENCE_VERSION);
		assertThat(version.get("code")).isEqualTo("PROTOCOLO-FIRMADO-1");
		assertThat(version.get("label")).isEqualTo("Protocolo de monitoreo firmado, versión 2.0");
		assertThat(version.get("source_note")).isEqualTo("PRD §10.3, Protocolo §5");

		Map<String, Map<String, Object>> rules = new LinkedHashMap<>();
		jdbc.queryForList("SELECT * FROM inc_reference_rule WHERE version_id = ?", REFERENCE_VERSION)
				.forEach(rule -> rules.put((String) rule.get("incident_type"), rule));
		assertThat(rules.keySet()).containsExactlyInAnyOrder("WAIT_FOR_CONNECTION", "DISCONNECTION",
				"CONNECTOR_FAILURE", "CONNECTOR_STUCK", "CANNOT_STOP_CHARGING", "KIA_PERSISTENT_OUTAGE",
				"INTERMITTENCY");
		assertThat(rules.get("DISCONNECTION")).containsEntry("recovery_max_min", 300)
				.containsEntry("recovery_extendable", true).containsEntry("proposed_priority", "HIGH");
		assertThat(rules.get("CONNECTOR_FAILURE")).containsEntry("recovery_max_min", 180);
		assertThat(rules.get("CONNECTOR_STUCK")).containsEntry("recovery_max_min", 120)
				.containsEntry("proposed_priority", "CRITICAL");
		assertThat(rules.get("WAIT_FOR_CONNECTION")).containsEntry("escalation_max_min", 60)
				.containsEntry("validation_max_min", 5);
		assertThat(rules.get("CANNOT_STOP_CHARGING")).containsEntry("validation_max_min", 5)
				.containsEntry("recovery_max_min", null).containsEntry("proposed_priority", "CRITICAL");
		assertThat(rules.get("KIA_PERSISTENT_OUTAGE")).containsEntry("persistent_after_min", 30);
		assertThat(rules.get("INTERMITTENCY")).containsEntry("proposed_priority", "MEDIUM");
	}

	@Test
	void ticketCodeKeepsAtLeastThreeDigitsAndNeverTruncates() {
		insert(equipment(86, "DISCONNECTION"));
		insert(equipment(999, "DISCONNECTION"));
		insert(equipment(1000, "DISCONNECTION"));
		insert(equipment(12345, "DISCONNECTION"));
		insert(equipment(7, "DISCONNECTION"));
		assertThat(jdbc.queryForList("SELECT ticket_code FROM inc_ticket ORDER BY ticket_number", String.class))
				.containsExactly("TICKET-007", "TICKET-086", "TICKET-999", "TICKET-1000", "TICKET-12345");
	}

	@Test
	void ticketNumberAndClientRequestAreUnique() {
		UUID request = UUID.randomUUID();
		Map<String, Object> first = equipment(500, "DISCONNECTION");
		first.put("client_request_id", request);
		insert(first);

		assertRejected(() -> insert(equipment(500, "DISCONNECTION")));
		Map<String, Object> sameRequest = equipment(501, "DISCONNECTION");
		sameRequest.put("client_request_id", request);
		assertRejected(() -> insert(sameRequest));
	}

	@Test
	void equipmentTicketsRequireTheirEquipmentSnapshotAndRejectOtherClassColumns() {
		insert(equipment(600, "CONNECTOR_FAILURE"));

		assertRejected(() -> insert(without(equipment(601, "DISCONNECTION"), "equipment_id")));
		assertRejected(() -> insert(without(equipment(602, "DISCONNECTION"), "network_at_t0")));
		assertRejected(() -> insert(without(equipment(603, "DISCONNECTION"), "equipment_label_at_t0")));
		assertRejected(() -> insert(with(equipment(604, "DISCONNECTION"), "element_name", "Barrera")));
		assertRejected(() -> insert(with(equipment(605, "DISCONNECTION"), "chatbot_scope", "TOTAL")));
		assertRejected(() -> insert(with(equipment(606, "DISCONNECTION"), "manual_site_name", "Sitio")));
		assertRejected(() -> insert(with(equipment(607, "DISCONNECTION"), "network_at_t0", "OTRA")));
	}

	@Test
	void chatbotTicketsHaveNoEquipmentSiteOrNetwork() {
		insert(chatbot(700, "CHATBOT_SERVICE_FAILURE"));
		Map<String, Object> partial = chatbot(701, "UNCATALOGED");
		partial.put("chatbot_scope", "PARTIAL");
		insert(partial);

		assertRejected(() -> insert(with(chatbot(702, "CHATBOT_SERVICE_FAILURE"), "equipment_id", UUID.randomUUID())));
		assertRejected(() -> insert(with(chatbot(703, "CHATBOT_SERVICE_FAILURE"), "network_at_t0", "KIA")));
		assertRejected(() -> insert(with(chatbot(704, "CHATBOT_SERVICE_FAILURE"), "site_element_kind", "OTHER")));
		assertRejected(() -> insert(with(chatbot(705, "CHATBOT_SERVICE_FAILURE"), "chatbot_scope", "ALGO")));
	}

	@Test
	void siteElementTicketsNeedExactlyOneLocationOption() {
		insert(siteWithReference(800, "VEHICLE_BARRIER", null));
		insert(siteWithReference(801, "OTHER", "Porton"));
		insert(siteWithManualLocation(802, "VEHICLE_BARRIER", null));

		assertRejected(() -> insert(without(siteWithReference(803, "VEHICLE_BARRIER", null),
				"reference_equipment_id")));
		assertRejected(() -> insert(with(siteWithReference(804, "VEHICLE_BARRIER", null), "manual_site_name",
				"Sitio")));
		assertRejected(() -> insert(without(siteWithManualLocation(805, "VEHICLE_BARRIER", null),
				"manual_location")));
		assertRejected(() -> insert(without(siteWithManualLocation(806, "VEHICLE_BARRIER", null),
				"manual_site_name")));
		assertRejected(() -> insert(without(siteWithReference(807, "VEHICLE_BARRIER", null),
				"site_element_kind")));
		assertRejected(() -> insert(siteWithReference(808, "OTHER", null)));
		assertRejected(() -> insert(with(siteWithReference(809, "VEHICLE_BARRIER", null), "network_at_t0", "KIA")));
		assertRejected(() -> insert(with(siteWithReference(810, "VEHICLE_BARRIER", null), "equipment_id",
				UUID.randomUUID())));
	}

	@Test
	void incidentTypeMustBeCompatibleWithTheElementClass() {
		assertRejected(() -> insert(equipment(900, "CHATBOT_SERVICE_FAILURE")));
		assertRejected(() -> insert(equipment(901, "SITE_ELEMENT_FAILURE")));
		assertRejected(() -> insert(chatbot(902, "DISCONNECTION")));
		assertRejected(() -> insert(chatbot(903, "SITE_ELEMENT_FAILURE")));
		assertRejected(() -> insert(siteWithReference(904, "VEHICLE_BARRIER", null, "DISCONNECTION")));
		assertRejected(() -> insert(siteWithReference(905, "VEHICLE_BARRIER", null, "CHATBOT_SERVICE_FAILURE")));
		assertRejected(() -> insert(equipment(906, "TIPO_INVENTADO")));

		insert(equipment(910, "UNCATALOGED"));
		insert(chatbot(911, "UNCATALOGED"));
		insert(siteWithReference(912, "VEHICLE_BARRIER", null, "UNCATALOGED"));
		insert(siteWithReference(913, "VEHICLE_BARRIER", null, "SITE_ELEMENT_FAILURE"));
	}

	@Test
	void reportOriginOtherRequiresADetail() {
		assertRejected(() -> insert(with(equipment(950, "DISCONNECTION"), "report_origin", "OTHER")));
		Map<String, Object> accepted = with(equipment(951, "DISCONNECTION"), "report_origin", "OTHER");
		accepted.put("report_origin_detail", "Llamada de un vecino");
		insert(accepted);
		assertRejected(() -> insert(with(equipment(952, "DISCONNECTION"), "report_origin", "DESCONOCIDO")));
	}

	@Test
	void milestonesRejectInvalidStatesAndT4WithoutATime() {
		UUID episode = episode(equipment(1100, "DISCONNECTION"), 1, "OPEN");

		milestone(episode, "T0", "RECORDED", true, null);
		milestone(episode, "T1", "PENDING", false, null);
		milestone(episode, "T2", "NOT_APPLICABLE", false, "No hubo apoyo");
		milestone(episode, "T3", "NO_DATA", false, "Se desconoce la hora real");

		assertRejected(() -> milestone(episode, "T4", "NO_DATA", false, "Sin dato"));
		assertRejected(() -> milestone(episode, "T4", "NOT_APPLICABLE", false, "No aplica"));
		assertRejected(() -> milestone(episode, "T4", "RECORDED", false, null));
		assertRejected(() -> milestone(episode, "T4", "PENDING", true, null));
		assertRejected(() -> milestone(episode, "T5", "PENDING", false, null));

		UUID other = episode(equipment(1101, "DISCONNECTION"), 1, "OPEN");
		assertRejected(() -> milestone(other, "T0", "PENDING", false, null));
		assertRejected(() -> milestone(other, "T1", "NOT_APPLICABLE", false, null));
		assertRejected(() -> milestone(other, "T1", "NO_DATA", false, " "));
	}

	@Test
	void closedEpisodesNeedResultCauseAndReopeningReason() {
		Map<String, Object> ticket = equipment(1200, "DISCONNECTION");
		UUID ticketId = insert(ticket);

		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", null, null, null, null));
		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", "RECOVERED", null, null, null));
		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", "RECOVERED", "CONFIRMED", null, null));
		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", "DUPLICATE", "UNDETERMINED", null, null));
		assertRejected(() -> episodeRow(ticketId, 2, "OPEN", null, null, null, null));
		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", "OTRO", "UNDETERMINED", null, null));

		episodeRow(ticketId, 1, "CLOSED", "RECOVERED", "UNDETERMINED", null, null);
		episodeRow(ticketId, 2, "OPEN", null, null, null, "El problema volvio");
		assertRejected(() -> episodeRow(ticketId, 2, "OPEN", null, null, null, "Duplicado"));
	}

	@Test
	void probableAndConfirmedCausesNeedADescription() {
		UUID ticketId = insert(equipment(1250, "DISCONNECTION"));
		assertRejected(() -> episodeRow(ticketId, 1, "CLOSED", "RECOVERED", "PROBABLE", " ", null));
		episodeRow(ticketId, 1, "CLOSED", "RECOVERED", "PROBABLE", "Corte de energia", null);
	}

	@Test
	void escalationsNeedATargetKindAndEventTypesAreClosedList() {
		UUID ticketId = insert(equipment(1300, "WAIT_FOR_CONNECTION"));

		event(ticketId, "ESCALATED", "TECHNICAL");
		event(ticketId, "ESCALATED", "SUPERVISION");
		event(ticketId, "DATA_CORRECTED", null);
		assertRejected(() -> event(ticketId, "ESCALATED", null));
		assertRejected(() -> event(ticketId, "ESCALATED", "OTRO_DESTINO"));
		assertRejected(() -> event(ticketId, "NOTIFICACION", null));
	}

	@Test
	void relationsStoreOneRowPerOrderedPair() {
		UUID first = insert(equipment(1400, "DISCONNECTION"));
		UUID second = insert(equipment(1401, "DISCONNECTION"));
		// PostgreSQL ordena los uuid por bytes sin signo, igual que su texto hexadecimal; UUID.compareTo de Java
		// compara con signo y no coincide. El dominio debe ordenar el par por su texto.
		UUID low = first.toString().compareTo(second.toString()) < 0 ? first : second;
		UUID high = low.equals(first) ? second : first;

		jdbc.update("INSERT INTO inc_ticket_relation (ticket_id, related_ticket_id, kind, created_at) VALUES (?, ?, ?, now())",
				low, high, "COMMON_FAILURE");
		assertRejected(() -> jdbc.update(
				"INSERT INTO inc_ticket_relation (ticket_id, related_ticket_id, kind, created_at) VALUES (?, ?, ?, now())",
				high, low, "COMMON_FAILURE"));
		assertRejected(() -> jdbc.update(
				"INSERT INTO inc_ticket_relation (ticket_id, related_ticket_id, kind, created_at) VALUES (?, ?, ?, now())",
				low, low, "COMMON_FAILURE"));
	}

	@Test
	void visitsCancelledOrPostponedNeedTheirJustification() {
		UUID ticketId = insert(equipment(1500, "DISCONNECTION"));
		UUID episode = episodeRow(ticketId, 1, "OPEN", null, null, null, null);

		visit(ticketId, episode, "REQUESTED", null, null, null);
		assertRejected(() -> visit(ticketId, episode, "CANCELLED", null, null, null));
		assertRejected(() -> visit(ticketId, episode, "POSTPONED", "Supervisor", null, null));
		visit(ticketId, episode, "POSTPONED", "Supervisor", "Mensaje de WhatsApp", "Si persiste 24 h");
		assertRejected(() -> visit(ticketId, episode, "VISITADA", null, null, null));
	}

	@Test
	void communicationsAndEvidenceKeepTheirClosedLists() {
		UUID ticketId = insert(equipment(1600, "DISCONNECTION"));
		UUID episode = episodeRow(ticketId, 1, "OPEN", null, null, null, null);

		communication(ticketId, episode, "KIA_ELECTROLINERAS", null, "INITIAL");
		communication(ticketId, episode, "OTHER", "Llamada", "UPDATE");
		assertRejected(() -> communication(ticketId, episode, "OTHER", null, "UPDATE"));
		assertRejected(() -> communication(ticketId, episode, "TELEGRAM", null, "UPDATE"));
		assertRejected(() -> communication(ticketId, episode, "EMAIL", null, "OTRO"));

		evidence(ticketId, "PDF", "GENERAL", 10);
		evidence(ticketId, "WEBP", "FINAL", 10);
		assertRejected(() -> evidence(ticketId, "EXE", "GENERAL", 10));
		assertRejected(() -> evidence(ticketId, "PNG", "OTRA", 10));
		assertRejected(() -> evidence(ticketId, "PNG", "GENERAL", 0));
	}

	@Test
	void declaresTheIndexesTheListingAndTheDuplicateWarningNeed() {
		assertThat(indexes()).contains("ix_inc_ticket_state_activity", "ix_inc_ticket_duplicate_open",
				"ix_inc_event_ticket", "ix_inc_event_notifiable", "ix_inc_milestone_revision_episode");
		assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_inc_ticket_duplicate_open'",
				String.class)).contains("WHERE");
		assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_inc_event_notifiable'",
				String.class)).contains("WHERE");
	}

	// ---- ayudas ----

	private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID insert(Map<String, Object> columns) {
		jdbc.update("INSERT INTO inc_ticket (" + String.join(", ", columns.keySet()) + ") VALUES ("
				+ String.join(", ", columns.keySet().stream().map(key -> "?").toList()) + ")",
				columns.values().toArray());
		return (UUID) columns.get("id");
	}

	private static Map<String, Object> base(int number, String elementClass, String type) {
		Map<String, Object> columns = new LinkedHashMap<>();
		OffsetDateTime now = OffsetDateTime.now();
		columns.put("id", UUID.randomUUID());
		columns.put("ticket_number", number);
		columns.put("client_request_id", UUID.randomUUID());
		columns.put("state", "OPEN");
		columns.put("element_class", elementClass);
		columns.put("incident_type", type);
		columns.put("report_origin", "PLATFORM");
		columns.put("description", "Descripcion de prueba");
		columns.put("priority", "MEDIUM");
		columns.put("current_responsible_id", UUID.randomUUID());
		columns.put("current_responsible_name", "Analista de prueba");
		columns.put("duplicate_key", "K-" + number);
		columns.put("last_activity_at", now);
		columns.put("created_at", now);
		columns.put("updated_at", now);
		columns.put("version", 0L);
		return columns;
	}

	private static Map<String, Object> equipment(int number, String type) {
		Map<String, Object> columns = base(number, "EQUIPMENT", type);
		columns.put("equipment_id", UUID.randomUUID());
		columns.put("equipment_label_at_t0", "Equipo de prueba");
		columns.put("network_at_t0", "KIA");
		columns.put("infrastructure_type_at_t0", "CHARGING_POINT");
		return columns;
	}

	private static Map<String, Object> chatbot(int number, String type) {
		return base(number, "CHATBOT_SERVICE", type);
	}

	private static Map<String, Object> siteWithReference(int number, String kind, String name) {
		return siteWithReference(number, kind, name, "SITE_ELEMENT_FAILURE");
	}

	private static Map<String, Object> siteWithReference(int number, String kind, String name, String type) {
		Map<String, Object> columns = base(number, "SITE_ELEMENT", type);
		columns.put("site_element_kind", kind);
		if (name != null) {
			columns.put("element_name", name);
		}
		columns.put("reference_equipment_id", UUID.randomUUID());
		return columns;
	}

	private static Map<String, Object> siteWithManualLocation(int number, String kind, String name) {
		Map<String, Object> columns = base(number, "SITE_ELEMENT", "SITE_ELEMENT_FAILURE");
		columns.put("site_element_kind", kind);
		if (name != null) {
			columns.put("element_name", name);
		}
		columns.put("manual_site_name", "Sitio sin equipos");
		columns.put("manual_location", "Via principal");
		return columns;
	}

	private static Map<String, Object> with(Map<String, Object> columns, String key, Object value) {
		columns.put(key, value);
		return columns;
	}

	private static Map<String, Object> without(Map<String, Object> columns, String key) {
		columns.remove(key);
		return columns;
	}

	private UUID episode(Map<String, Object> ticket, int ordinal, String state) {
		UUID ticketId = insert(ticket);
		return episodeRow(ticketId, ordinal, state, null, null, null, null);
	}

	private UUID episodeRow(UUID ticketId, int ordinal, String state, String result, String causeKind,
			String causeText, String reopenReason) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_episode (id, ticket_id, ordinal, state, reference_version_id, reopen_reason,
				    closure_result, cause_kind, cause_text, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", id, ticketId, (short) ordinal, state, REFERENCE_VERSION, reopenReason, result, causeKind,
				causeText == null && "CONFIRMED".equals(causeKind) ? null : causeText, now, now);
		return id;
	}

	private void milestone(UUID episode, String kind, String status, boolean withTime, String reason) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_milestone (episode_id, kind, status, occurred_at, reason, recorded_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", episode, kind, status, withTime ? now : null, reason, now);
	}

	private void event(UUID ticketId, String type, String targetKind) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_event (id, ticket_id, type, occurred_at, recorded_at, target_kind)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), ticketId, type, now, now, targetKind);
	}

	private void visit(UUID ticketId, UUID episode, String stage, String decidedBy, String note, String condition) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_visit (id, ticket_id, episode_id, stage, cancel_decided_by, cancel_evidence_note,
				    reescalation_condition, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", UUID.randomUUID(), ticketId, episode, stage, decidedBy, note, condition, now, now);
	}

	private void communication(UUID ticketId, UUID episode, String channel, String detail, String kind) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_communication (id, ticket_id, episode_id, channel, channel_detail, kind, sent_at,
				    recorded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), ticketId, episode, channel, detail, kind, now, now);
	}

	private void evidence(UUID ticketId, String format, String purpose, long size) {
		jdbc.update("""
				INSERT INTO inc_evidence (id, ticket_id, purpose, storage_ref, format, size_bytes, sha256,
				    original_name, uploaded_at)
				VALUES (?, ?, ?, 'ref', ?, ?, ?, 'foto.png', now())
				""", UUID.randomUUID(), ticketId, purpose, format, size, "a".repeat(64));
	}

	private List<String> tables() {
		return jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);
	}

	private List<String> indexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
	}
}
