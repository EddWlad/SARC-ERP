package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Regla de referencia de un tipo en una version (FR-051). Un tipo sin fila no tiene referencia: su tiempo se muestra
 * sin juicio de cumplimiento (FR-054). Las filas no se modifican una vez sembradas.
 */
@Entity
@Table(name = "inc_reference_rule")
@IdClass(ReferenceRuleId.class)
public class ReferenceRule {

	@Id
	@Column(name = "version_id", updatable = false)
	private UUID versionId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "incident_type", updatable = false)
	private IncidentType incidentType;

	@Column(updatable = false)
	private Integer validationMaxMin;

	@Column(updatable = false)
	private Integer recoveryMaxMin;

	@Column(nullable = false, updatable = false)
	private boolean recoveryExtendable;

	@Column(updatable = false)
	private Integer escalationMaxMin;

	@Column(updatable = false)
	private Integer persistentAfterMin;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Priority proposedPriority;

	protected ReferenceRule() {
	}

	/** Una regla de una version: la siembran las migraciones; esta fabrica sirve a las pruebas del dominio. */
	public static ReferenceRule of(UUID versionId, IncidentType type, Integer validationMaxMin, Integer recoveryMaxMin,
			boolean recoveryExtendable, Integer escalationMaxMin, Integer persistentAfterMin, Priority proposedPriority) {
		ReferenceRule rule = new ReferenceRule();
		rule.versionId = versionId;
		rule.incidentType = type;
		rule.validationMaxMin = validationMaxMin;
		rule.recoveryMaxMin = recoveryMaxMin;
		rule.recoveryExtendable = recoveryExtendable;
		rule.escalationMaxMin = escalationMaxMin;
		rule.persistentAfterMin = persistentAfterMin;
		rule.proposedPriority = proposedPriority;
		return rule;
	}

	public UUID getVersionId() {
		return versionId;
	}

	public IncidentType getIncidentType() {
		return incidentType;
	}

	/** Validacion inicial (T1−T0) maxima, en minutos. */
	public Optional<Integer> validationMaxMin() {
		return Optional.ofNullable(validationMaxMin);
	}

	/** Recuperacion (T3−T0) maxima, en minutos; vacio si no hay maximo aprobado. */
	public Optional<Integer> recoveryMaxMin() {
		return Optional.ofNullable(recoveryMaxMin);
	}

	/** Si el maximo de recuperacion se puede ampliar con justificacion (solo Desconexion). */
	public boolean isRecoveryExtendable() {
		return recoveryExtendable;
	}

	/** Escalamiento a tecnicos maximo desde T0, en minutos (Espera de conexion). */
	public Optional<Integer> escalationMaxMin() {
		return Optional.ofNullable(escalationMaxMin);
	}

	/**
	 * Minutos tras los cuales una caida del punto KIA es persistente (P-04). Es persistente solo si la duracion exacta,
	 * en segundos, es estrictamente mayor que este valor por 60: nunca se redondea a minutos.
	 */
	public Optional<Integer> persistentAfterMin() {
		return Optional.ofNullable(persistentAfterMin);
	}

	public Priority getProposedPriority() {
		return proposedPriority;
	}
}
