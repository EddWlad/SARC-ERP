package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Version anterior de una sesion, conservada al aplicar una correccion. Solo se escribe. */
@Entity
@Table(name = "energy_session_revision")
public class SessionRevision {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private int revision;

	@Column(nullable = false, updatable = false)
	private Instant startedAt;

	@Column(updatable = false)
	private Instant endedAt;

	@Column(updatable = false, precision = 12, scale = SessionReading.ENERGY_SCALE)
	private BigDecimal energyKwh;

	@Column(updatable = false, length = 150)
	private String sourceSite;

	@Column(updatable = false, length = 150)
	private String sourceCharger;

	@Column(updatable = false, length = 80)
	private String sourceSerial;

	@Column(updatable = false, length = 150)
	private String sourceConnector;

	/** Equipo al que estaba vinculada esa version (cambia al reasignar). */
	@Column(updatable = false)
	private UUID equipmentId;

	@Column(nullable = false, updatable = false)
	private UUID importBatchId;

	@Column(nullable = false, updatable = false)
	private Instant supersededAt;

	@Column(updatable = false)
	private UUID supersededBy;

	@Column(nullable = false, updatable = false)
	private UUID supersededByBatch;

	protected SessionRevision() {
	}

	static SessionRevision of(EnergySession session, UUID supersedingBatch, UUID actorId, Instant at) {
		SessionRevision revision = new SessionRevision();
		SessionReading reading = session.reading();
		revision.id = UUID.randomUUID();
		revision.sessionId = session.getId();
		revision.revision = session.getRevision();
		revision.startedAt = reading.startedAt();
		revision.endedAt = reading.endedAt();
		revision.energyKwh = reading.energyKwh();
		revision.sourceSite = reading.site();
		revision.sourceCharger = reading.charger();
		revision.sourceSerial = reading.serial();
		revision.sourceConnector = reading.connector();
		revision.equipmentId = session.getEquipmentId();
		revision.importBatchId = session.getImportBatchId();
		revision.supersededAt = at;
		revision.supersededBy = actorId;
		revision.supersededByBatch = supersedingBatch;
		return revision;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getRevision() {
		return revision;
	}

	public BigDecimal getEnergyKwh() {
		return energyKwh;
	}

	public UUID getEquipmentId() {
		return equipmentId;
	}
}
