package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Version anterior de una sesion, conservada antes de cada cambio de sus datos: una correccion o
 * reasignacion desde una reimportacion, una edicion individual o una baja logica (Feature 009, US3). Solo
 * se escribe. Las de importacion guardan el lote que las reemplazo; las de edicion y baja, el motivo.
 */
@Entity
@Table(name = "energy_session_revision")
public class SessionRevision {

	/** Que operacion reemplazo esta version. */
	public enum Kind {
		IMPORT, EDIT, DELETION
	}

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private int revision;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private Kind kind;

	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	@Column(updatable = false)
	private Instant endedAt;

	@Column(updatable = false)
	private Integer sourceUtcOffsetMinutes;

	@Column(updatable = false)
	private Integer sourceEndUtcOffsetMinutes;

	@Column(updatable = false, precision = 12, scale = SessionReading.ENERGY_SCALE)
	private BigDecimal energyKwh;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private EnergyReviewStatus energyReview;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false, length = 20)
	private SessionState state;

	@Column(updatable = false, length = 150)
	private String sourceSite;

	@Column(updatable = false, length = 150)
	private String sourceCharger;

	@Column(updatable = false, length = 80)
	private String sourceSerial;

	@Column(updatable = false, length = 150)
	private String sourceConnector;

	/** Equipo al que estaba vinculada esa version (cambia al reasignar o editar). */
	@Column(updatable = false)
	private UUID equipmentId;

	@Column(nullable = false, updatable = false)
	private UUID importBatchId;

	@Column(nullable = false, updatable = false)
	private Instant supersededAt;

	@Column(updatable = false)
	private UUID supersededBy;

	/** Lote que reemplazo esta version; nulo en ediciones y bajas, que no vienen de un lote. */
	@Column(updatable = false)
	private UUID supersededByBatch;

	@Column(updatable = false, length = 500)
	private String reason;

	protected SessionRevision() {
	}

	static SessionRevision of(EnergySession session, UUID supersedingBatch, UUID actorId, Instant at) {
		SessionRevision revision = snapshot(session, Kind.IMPORT, actorId, at);
		revision.supersededByBatch = supersedingBatch;
		return revision;
	}

	static SessionRevision edited(EnergySession session, String reason, UUID actorId, Instant at) {
		SessionRevision revision = snapshot(session, Kind.EDIT, actorId, at);
		revision.reason = reason;
		return revision;
	}

	static SessionRevision deleted(EnergySession session, String reason, UUID actorId, Instant at) {
		SessionRevision revision = snapshot(session, Kind.DELETION, actorId, at);
		revision.reason = reason;
		return revision;
	}

	private static SessionRevision snapshot(EnergySession session, Kind kind, UUID actorId, Instant at) {
		SessionRevision revision = new SessionRevision();
		SessionReading reading = session.reading();
		revision.id = UUID.randomUUID();
		revision.sessionId = session.getId();
		revision.revision = session.getRevision();
		revision.kind = kind;
		revision.startedAt = reading.startedAt();
		revision.endedAt = reading.endedAt();
		revision.sourceUtcOffsetMinutes = session.getSourceUtcOffsetMinutes();
		revision.sourceEndUtcOffsetMinutes = session.getSourceEndUtcOffsetMinutes();
		revision.energyKwh = reading.energyKwh();
		revision.energyReview = session.getEnergyReview();
		revision.state = session.getState();
		revision.sourceSite = reading.site();
		revision.sourceCharger = reading.charger();
		revision.sourceSerial = reading.serial();
		revision.sourceConnector = reading.connector();
		revision.equipmentId = session.getEquipmentId();
		revision.importBatchId = session.getImportBatchId();
		revision.supersededAt = at;
		revision.supersededBy = actorId;
		return revision;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getRevision() {
		return revision;
	}

	public Kind getKind() {
		return kind;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public Integer getSourceUtcOffsetMinutes() {
		return sourceUtcOffsetMinutes;
	}

	public Integer getSourceEndUtcOffsetMinutes() {
		return sourceEndUtcOffsetMinutes;
	}

	public BigDecimal getEnergyKwh() {
		return energyKwh;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}

	public EnergyReviewStatus getEnergyReview() {
		return energyReview;
	}

	public SessionState getState() {
		return state;
	}

	public Instant getSupersededAt() {
		return supersededAt;
	}

	public UUID getSupersededBy() {
		return supersededBy;
	}

	public String getReason() {
		return reason;
	}
}
