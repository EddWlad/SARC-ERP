package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Decision inmutable de calidad sobre una lectura y version concretas. */
@Entity
@Table(name = "energy_session_review")
public class EnergyReview {
	@Id
	private UUID id;
	@Column(nullable = false, updatable = false)
	private UUID sessionId;
	@Column(nullable = false, updatable = false)
	private int sessionRevision;
	@Column(nullable = false, updatable = false)
	private long sessionVersion;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private EnergyReviewStatus decision;
	@Column(nullable = false, updatable = false, length = 500)
	private String justification;
	@Column(nullable = false, updatable = false, precision = 12, scale = 3)
	private BigDecimal energyKwh;
	@Column(updatable = false)
	private UUID decidedBy;
	@Column(nullable = false, updatable = false)
	private Instant decidedAt;

	protected EnergyReview() {
	}

	static EnergyReview of(EnergySession session, EnergyReviewStatus decision, String justification,
			UUID actor, Instant at) {
		if (decision == null || decision == EnergyReviewStatus.PENDING) {
			throw new BusinessRuleException("INVALID_ENERGY_REVIEW_DECISION", "Indique aprobar o rechazar la energía");
		}
		String reason = justification == null ? "" : justification.strip();
		if (reason.isBlank()) {
			throw new BusinessRuleException("ENERGY_REVIEW_JUSTIFICATION_REQUIRED", "Ingrese una justificación");
		}
		if (reason.length() > 500) {
			throw new BusinessRuleException("ENERGY_REVIEW_JUSTIFICATION_TOO_LONG", "La justificación admite hasta 500 caracteres");
		}
		EnergyReview review = new EnergyReview();
		review.id = UUID.randomUUID();
		review.sessionId = session.getId();
		review.sessionRevision = session.getRevision();
		review.sessionVersion = session.getVersion() == null ? 0 : session.getVersion();
		review.decision = decision;
		review.justification = reason;
		review.energyKwh = session.getEnergyKwh();
		review.decidedBy = actor;
		review.decidedAt = at;
		return review;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getSessionRevision() {
		return sessionRevision;
	}

	public long getSessionVersion() {
		return sessionVersion;
	}

	public EnergyReviewStatus getDecision() {
		return decision;
	}

	public String getJustification() {
		return justification;
	}

	public BigDecimal getEnergyKwh() {
		return energyKwh;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

}
