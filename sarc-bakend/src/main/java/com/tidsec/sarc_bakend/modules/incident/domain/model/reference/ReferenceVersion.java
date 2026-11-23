package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Version inmutable de las referencias de tiempo (FR-051a, P-03). Cada episodio guarda la vigente en su T0: rige la
 * ultima con {@code effectiveFrom} anterior o igual a T0. Una vez sembrada no se modifica; un cambio crea otra version
 * con otra migracion. Las referencias son operativas, no un SLA contractual.
 */
@Entity
@Table(name = "inc_reference_version")
public class ReferenceVersion {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String code;

	@Column(nullable = false, updatable = false)
	private String label;

	@Column(nullable = false, updatable = false)
	private Instant effectiveFrom;

	@Column(nullable = false, updatable = false)
	private String sourceNote;

	protected ReferenceVersion() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}

	public String getSourceNote() {
		return sourceNote;
	}
}
