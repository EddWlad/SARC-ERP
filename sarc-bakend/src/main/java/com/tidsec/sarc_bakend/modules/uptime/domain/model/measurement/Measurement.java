package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Instant;
import java.time.LocalDate;
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

	@Column
	private Instant periodStart;

	@Column
	private Instant periodEnd;

	@Column(nullable = false, updatable = false, length = 40)
	private String periodZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_precision", nullable = false, length = 32)
    private TemporalPrecision temporalPrecision = TemporalPrecision.KNOWN_RANGE;
    private LocalDate referenceWeek;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PeriodType referencePeriodType;

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

    public static Measurement open(UUID connectorId, UUID equipmentId, MeasurementSource source,
            MeasurementPeriod period) {
        Measurement value = new Measurement(connectorId,equipmentId,source,period.start(),period.end(),period.zone());
        value.changePeriod(period);
        return value;
    }

    /** Cambia solo metadata temporal. Las revisiones, punteros y exclusiones no se reinician. */
    public void changePeriod(MeasurementPeriod period) {
        if (!periodZone.equals(period.zone()))
            throw new BusinessRuleException("UPTIME_PERIOD_INVALID","La zona operativa no puede cambiar.");
        temporalPrecision = period.precision();
        periodStart = period.start(); periodEnd = period.end();
        referenceWeek = period.referenceWeek(); referencePeriodType = period.referencePeriodType();
    }
    public MeasurementPeriod getPeriod() {
        return new MeasurementPeriod(temporalPrecision,periodStart,periodEnd,periodZone,referenceWeek,referencePeriodType);
    }
    public TemporalPrecision getTemporalPrecision() { return temporalPrecision; }
    public LocalDate getReferenceWeek() { return referenceWeek; }
    public PeriodType getReferencePeriodType() { return referencePeriodType; }
    public PeriodType effectivePeriodType(PeriodType original) { return getPeriod().effectiveType(original); }

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
        if (temporalPrecision == TemporalPrecision.REQUIRES_RECONCILIATION)
            throw new BusinessRuleException("UPTIME_TEMPORAL_RECONCILIATION_REQUIRED",
                "Concilia el periodo antes de validar la medicion.");
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
