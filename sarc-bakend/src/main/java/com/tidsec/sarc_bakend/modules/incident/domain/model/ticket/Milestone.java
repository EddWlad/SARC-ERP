package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Un hito (T0 a T4) de un episodio, con su estado y su hora (FR-040 a FR-043). Solo {@code RECORDED} tiene hora: el
 * sistema nunca inventa una ni convierte un hito sin hora en cero. Cada cambio de un hito anexa una
 * {@link MilestoneRevision}; esta fila guarda el valor vigente.
 */
@Entity
@Table(name = "inc_milestone")
@IdClass(MilestoneId.class)
public class Milestone {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "episode_id", nullable = false, updatable = false)
	private Episode episode;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "kind", length = 2, updatable = false)
	private MilestoneKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MilestoneStatus status;

	private Instant occurredAt;

	private String reason;

	@Column(nullable = false)
	private boolean retroactive;

	@Column(nullable = false)
	private boolean orderWarningAck;

	private UUID recordedBy;

	@Column(nullable = false)
	private Instant recordedAt;

	protected Milestone() {
	}

	private Milestone(Episode episode, MilestoneKind kind, MilestoneStatus status, Instant occurredAt, String reason,
			boolean retroactive, UUID recordedBy, Instant recordedAt) {
		this.episode = episode;
		this.kind = kind;
		this.status = status;
		this.occurredAt = occurredAt;
		this.reason = reason;
		this.retroactive = retroactive;
		this.recordedBy = recordedBy;
		this.recordedAt = recordedAt;
	}

	/** T0: siempre registrado, con la hora informada. */
	static Milestone recordedT0(Episode episode, Instant t0, boolean retroactive, String reason, UUID by, Instant now) {
		return new Milestone(episode, MilestoneKind.T0, MilestoneStatus.RECORDED, t0, reason, retroactive, by, now);
	}

	/** T1 a T4 nacen pendientes, sin hora. */
	static Milestone pending(Episode episode, MilestoneKind kind, Instant now) {
		return new Milestone(episode, kind, MilestoneStatus.PENDING, null, null, false, null, now);
	}

	/** Reemplaza el valor vigente; la revision con el valor anterior la anexa quien llama ({@link Episode}). */
	void change(MilestoneStatus newStatus, Instant newOccurredAt, String newReason, boolean newRetroactive,
			boolean orderAck, UUID by, Instant at) {
		this.status = newStatus;
		this.occurredAt = newOccurredAt;
		this.reason = newReason;
		this.retroactive = newRetroactive;
		this.orderWarningAck = orderAck;
		this.recordedBy = by;
		this.recordedAt = at;
	}

	public Episode getEpisode() {
		return episode;
	}

	public MilestoneKind getKind() {
		return kind;
	}

	public MilestoneStatus getStatus() {
		return status;
	}

	/** La hora del hito; nula si no esta registrado. */
	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getReason() {
		return reason;
	}

	public boolean isRetroactive() {
		return retroactive;
	}

	public boolean isOrderWarningAck() {
		return orderWarningAck;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
