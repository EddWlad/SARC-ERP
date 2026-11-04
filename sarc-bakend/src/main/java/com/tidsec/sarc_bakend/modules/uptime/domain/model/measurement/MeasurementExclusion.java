package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Evento de exclusion o restauracion. Solo se escribe. */
@Entity
@Table(name = "upt_measurement_exclusion")
public class MeasurementExclusion {

	public enum Action {
		EXCLUDED,
		RESTORED
	}

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID measurementId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Action action;

	@Column(nullable = false, updatable = false, length = 500)
	private String justification;

	@Column(nullable = false, updatable = false)
	private long measurementVersion;

	@Column(updatable = false)
	private UUID decidedBy;

	@Column(nullable = false, updatable = false)
	private Instant decidedAt;

	protected MeasurementExclusion() {
	}

	public MeasurementExclusion(UUID measurementId, Action action, String justification, long measurementVersion,
			UUID decidedBy, Instant decidedAt) {
		this.id = UUID.randomUUID();
		this.measurementId = measurementId;
		this.action = action;
		this.justification = justification;
		this.measurementVersion = measurementVersion;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public UUID getId() {
		return id;
	}

	public Action getAction() {
		return action;
	}

	public String getJustification() {
		return justification;
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}
}
