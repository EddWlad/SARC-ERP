package com.tidsec.sarc_bakend.modules.incident.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

/**
 * Siembra por SQL de tickets con episodios y hitos en fechas elegidas, para probar los informes de incidencias sin
 * depender del reloj: las fechas se calculan respecto de hoy. Un ticket se crea con {@link #insertEquipmentTicket} y su
 * episodio se escribe con los hitos que la prueba necesita.
 */
public abstract class IncidentReportSupport extends IncidentApiSupport {

	protected static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

	protected UUID referenceVersion;

	@BeforeEach
	void version() {
		referenceVersion = jdbc.queryForObject("SELECT id FROM inc_reference_version ORDER BY effective_from LIMIT 1", UUID.class);
	}

	protected static Instant at(LocalDate day) {
		return day.atStartOfDay(ZONE).toInstant();
	}

	protected static String iso(LocalDate day) {
		return day.toString();
	}

	protected static LocalDate last(LocalDate monthStart) {
		return monthStart.plusMonths(1).minusDays(1);
	}

	/** Un ticket de equipo con su episodio 1; {@code t4} nulo es un episodio abierto. */
	protected UUID seed(int number, Instant t0, Instant t4) {
		UUID ticket = insertEquipmentTicket(number);
		addEpisode(ticket, 1, t0, t4);
		jdbc.update("UPDATE inc_ticket SET state = ? WHERE id = ?", t4 == null ? "OPEN" : "CLOSED", ticket);
		return ticket;
	}

	protected void addEpisode(UUID ticket, int ordinal, Instant t0, Instant t4) {
		UUID episode = UUID.randomUUID();
		OffsetDateTime created = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
		boolean closed = t4 != null;
		jdbc.update("""
				INSERT INTO inc_episode (id, ticket_id, ordinal, state, reference_version_id, reopen_reason, closure_result,
				    cause_kind, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
				""", episode, ticket, ordinal, closed ? "CLOSED" : "OPEN", referenceVersion, ordinal > 1 ? "Reapertura" : null,
				closed ? "RECOVERED" : null, closed ? "UNDETERMINED" : null, created, created);
		milestone(episode, "T0", "RECORDED", t0, created);
		for (String kind : List.of("T1", "T2", "T3")) {
			milestone(episode, kind, "PENDING", null, created);
		}
		milestone(episode, "T4", closed ? "RECORDED" : "PENDING", t4, created);
	}

	protected void milestone(UUID episode, String kind, String status, Instant occurredAt, OffsetDateTime recordedAt) {
		jdbc.update("""
				INSERT INTO inc_milestone (episode_id, kind, status, occurred_at, retroactive, order_warning_ack, recorded_at)
				VALUES (?, ?, ?, ?, FALSE, FALSE, ?)
				""", episode, kind, status, occurredAt == null ? null : OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
				recordedAt);
	}

	/** Cambia un hito de un episodio ya sembrado (estado, hora y, si no esta registrado, el motivo que exige el esquema). */
	protected void setMilestone(UUID ticket, int ordinal, String kind, String status, Instant occurredAt) {
		jdbc.update("""
				UPDATE inc_milestone SET status = ?, occurred_at = ?, reason = ?
				WHERE kind = ? AND episode_id = (SELECT id FROM inc_episode WHERE ticket_id = ? AND ordinal = ?)
				""", status, occurredAt == null ? null : OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC),
				"RECORDED".equals(status) || "PENDING".equals(status) ? null : "Motivo de prueba", kind, ticket, ordinal);
	}

}
