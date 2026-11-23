package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Relacion entre dos tickets (FR-017). Se guarda una sola fila por par, con {@code ticketId < relatedTicketId}, y se
 * muestra en ambos sentidos. No fusiona hitos ni conteos.
 *
 * <p>El orden del par es el de PostgreSQL: los {@code uuid} se comparan por sus bytes sin signo, que equivale a
 * comparar su texto hexadecimal. {@link UUID#compareTo} de Java compara con signo y no coincide, asi que aqui se ordena
 * siempre por el texto.
 */
@Entity
@Table(name = "inc_ticket_relation")
@IdClass(TicketRelationId.class)
public class TicketRelation {

	@Id
	@Column(name = "ticket_id", updatable = false)
	private UUID ticketId;

	@Id
	@Column(name = "related_ticket_id", updatable = false)
	private UUID relatedTicketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private RelationKind kind;

	@Column(updatable = false)
	private String reason;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(updatable = false)
	private UUID createdBy;

	protected TicketRelation() {
	}

	private TicketRelation(UUID first, UUID second, RelationKind kind, String reason, UUID by, Instant at) {
		this.ticketId = first;
		this.relatedTicketId = second;
		this.kind = kind;
		this.reason = reason;
		this.createdBy = by;
		this.createdAt = at;
	}

	/** Relaciona dos tickets distintos, guardando el par en el orden de PostgreSQL. */
	public static TicketRelation between(UUID a, UUID b, RelationKind kind, String reason, UUID by, Instant at) {
		if (a.equals(b)) {
			throw new IllegalArgumentException("Un ticket no se relaciona consigo mismo");
		}
		boolean aFirst = a.toString().compareTo(b.toString()) < 0;
		return new TicketRelation(aFirst ? a : b, aFirst ? b : a, kind, reason, by, at);
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getRelatedTicketId() {
		return relatedTicketId;
	}

	public RelationKind getKind() {
		return kind;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	/** El otro ticket del par, visto desde {@code ticket}. */
	public UUID otherThan(UUID ticket) {
		return ticketId.equals(ticket) ? relatedTicketId : ticketId;
	}
}
