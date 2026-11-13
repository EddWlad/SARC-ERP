package com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Historial inmutable de las marcas de un dia revisado: quien marco o retiro, cuando y, al retirar, por que.
 * {@code pendingBatchesWarned} son los lotes sin confirmar que tocaban el dia cuando se marco (FR-045).
 */
@Entity
@Table(name = "itm_reviewed_day_event")
public class ReviewedDayEvent {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private LocalDate day;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private ReviewedDayAction action;

	@Column(updatable = false, length = 500)
	private String justification;

	@Column(nullable = false, updatable = false)
	private UUID actorId;

	@Column(name = "at", nullable = false, updatable = false)
	private Instant at;

	@Column(nullable = false, updatable = false)
	private int pendingBatchesWarned;

	protected ReviewedDayEvent() {
	}

	private ReviewedDayEvent(LocalDate day, ReviewedDayAction action, String justification, UUID actorId, Instant at,
			int pendingBatchesWarned) {
		this.id = UUID.randomUUID();
		this.day = day;
		this.action = action;
		this.justification = justification;
		this.actorId = actorId;
		this.at = at;
		this.pendingBatchesWarned = pendingBatchesWarned;
	}

	public static ReviewedDayEvent marked(LocalDate day, UUID actorId, Instant at, int pendingBatchesWarned) {
		return new ReviewedDayEvent(day, ReviewedDayAction.MARKED, null, actorId, at, pendingBatchesWarned);
	}

	public static ReviewedDayEvent withdrawn(LocalDate day, String justification, UUID actorId, Instant at) {
		return new ReviewedDayEvent(day, ReviewedDayAction.WITHDRAWN, justification.strip(), actorId, at, 0);
	}

	public UUID getId() {
		return id;
	}

	public LocalDate getDay() {
		return day;
	}

	public ReviewedDayAction getAction() {
		return action;
	}

	public String getJustification() {
		return justification;
	}

	public UUID getActorId() {
		return actorId;
	}

	public Instant getAt() {
		return at;
	}

	public int getPendingBatchesWarned() {
		return pendingBatchesWarned;
	}
}
