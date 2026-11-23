package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Revision de un hito: solo se anexan filas, nunca se actualizan ni se borran (research R3). Guarda el valor anterior y
 * el nuevo, la justificacion en las correcciones (FR-036), el autor y el momento del registro: con ella se reconstruye
 * lo que se sabia en un instante dado y se comprueban los informes ya emitidos. No tiene metodos que cambien su estado.
 */
@Entity
@Table(name = "inc_milestone_revision")
public class MilestoneRevision {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID episodeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", length = 2, nullable = false, updatable = false)
	private MilestoneKind kind;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private MilestoneStatus previousStatus;

	@Column(updatable = false)
	private Instant previousOccurredAt;

	@Column(updatable = false)
	private String previousReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private MilestoneStatus newStatus;

	@Column(updatable = false)
	private Instant newOccurredAt;

	@Column(updatable = false)
	private String newReason;

	@Column(updatable = false)
	private String justification;

	@Column(updatable = false)
	private UUID recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	protected MilestoneRevision() {
	}

	public MilestoneRevision(UUID episodeId, MilestoneKind kind, MilestoneStatus previousStatus,
			Instant previousOccurredAt, String previousReason, MilestoneStatus newStatus, Instant newOccurredAt,
			String newReason, String justification, UUID recordedBy, Instant recordedAt) {
		this.id = UUID.randomUUID();
		this.episodeId = episodeId;
		this.kind = kind;
		this.previousStatus = previousStatus;
		this.previousOccurredAt = previousOccurredAt;
		this.previousReason = previousReason;
		this.newStatus = newStatus;
		this.newOccurredAt = newOccurredAt;
		this.newReason = newReason;
		this.justification = justification;
		this.recordedBy = recordedBy;
		this.recordedAt = recordedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getEpisodeId() {
		return episodeId;
	}

	public MilestoneKind getKind() {
		return kind;
	}

	public MilestoneStatus getPreviousStatus() {
		return previousStatus;
	}

	public Instant getPreviousOccurredAt() {
		return previousOccurredAt;
	}

	public String getPreviousReason() {
		return previousReason;
	}

	public MilestoneStatus getNewStatus() {
		return newStatus;
	}

	public Instant getNewOccurredAt() {
		return newOccurredAt;
	}

	public String getNewReason() {
		return newReason;
	}

	public String getJustification() {
		return justification;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
