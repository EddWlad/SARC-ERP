package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Identidad de una medicion: conector, fuente y periodo real. Los valores viven en las revisiones. */
@Entity
@Table(name = "upt_measurement")
public class Measurement extends BaseAuditableEntity {

	@Column(nullable = false, updatable = false)
	private UUID connectorId;

	@Column(nullable = false, updatable = false)
	private UUID equipmentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private MeasurementSource source;

	@Column(nullable = false, updatable = false)
	private Instant periodStart;

	@Column(nullable = false, updatable = false)
	private Instant periodEnd;

	@Column(nullable = false, updatable = false, length = 40)
	private String periodZone;

	@Column(nullable = false)
	private int latestRevision;

	private Integer effectiveRevision;

	@Column(nullable = false)
	private boolean excluded;

	protected Measurement() {
	}

	private Measurement(UUID connectorId, UUID equipmentId, MeasurementSource source, Instant periodStart,
			Instant periodEnd, String periodZone) {
		super(UUID.randomUUID());
		this.connectorId = connectorId;
		this.equipmentId = equipmentId;
		this.source = source;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.periodZone = periodZone;
	}

	public static Measurement open(UUID connectorId, UUID equipmentId, MeasurementSource source, Instant periodStart,
			Instant periodEnd, String periodZone) {
		return new Measurement(connectorId, equipmentId, source, periodStart, periodEnd, periodZone);
	}

	/** Registra la primera revision, siempre pendiente. */
	public MeasurementRevision register(RevisionData data) {
		latestRevision = 1;
		return new MeasurementRevision(getId(), 1, data);
	}

	/** Nueva revision pendiente. Una pendiente anterior se reemplaza; la vigente sigue hasta validar. */
	public MeasurementRevision correct(RevisionData data, MeasurementRevision latest, Instant at) {
		if (latest.getReviewState() == ReviewState.PENDING) {
			latest.supersede(at);
		}
		latestRevision++;
		return new MeasurementRevision(getId(), latestRevision, data);
	}

	/** Valida la ultima revision. La vigente anterior deja de computar en este momento. */
	public void validate(MeasurementRevision latest, MeasurementRevision effective, UUID actor, Instant at,
			String justification) {
		if (latest.getReviewState() != ReviewState.PENDING) {
			throw new BusinessRuleException("UPTIME_NOTHING_TO_VALIDATE", "No hay una revision pendiente de validar.");
		}
		if (!latest.isValidatable()) {
			throw new BusinessRuleException("UPTIME_NOT_VALIDATABLE", "La medición debe corregirse antes de validarse.");
		}
		if (!latest.getObservations().isEmpty() && (justification == null || justification.isBlank())) {
			throw new BusinessRuleException("UPTIME_JUSTIFICATION_REQUIRED",
					"Justifica la validación de una medición observada.");
		}
		if (effective != null) {
			effective.supersede(at);
		}
		latest.validate(actor, at, justification);
		effectiveRevision = latest.getRevision();
	}

	/** Reversible: las revisiones no cambian. */
	public void exclude() {
		if (excluded) {
			throw new BusinessRuleException("UPTIME_ALREADY_EXCLUDED", "La medición ya está excluida.");
		}
		excluded = true;
	}

	public void restore() {
		if (!excluded) {
			throw new BusinessRuleException("UPTIME_NOT_EXCLUDED", "La medición no está excluida.");
		}
		excluded = false;
	}

	public UUID getConnectorId() {
		return connectorId;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public MeasurementSource getSource() {
		return source;
	}

	public Instant getPeriodStart() {
		return periodStart;
	}

	public Instant getPeriodEnd() {
		return periodEnd;
	}

	public String getPeriodZone() {
		return periodZone;
	}

	public int getLatestRevision() {
		return latestRevision;
	}

	public Integer getEffectiveRevision() {
		return effectiveRevision;
	}

	public boolean isExcluded() {
		return excluded;
	}
}
