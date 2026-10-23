package com.tidsec.sarc_bakend.shared.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Hecho auditable inmutable: quien hizo que, sobre que entidad y cuando. Solo se escribe. */
@Entity
@Table(name = "audit_event")
class AuditEvent {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(updatable = false)
	private UUID actorId;

	@Column(nullable = false, updatable = false, length = 60)
	private String action;

	@Column(nullable = false, updatable = false, length = 60)
	private String entityType;

	@Column(updatable = false)
	private UUID entityId;

	@Column(updatable = false, length = 64)
	private String traceId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false, columnDefinition = "jsonb")
	private Map<String, String> details;

	protected AuditEvent() {
	}

	AuditEvent(Instant occurredAt, UUID actorId, String action, String entityType, UUID entityId, String traceId,
			Map<String, String> details) {
		this.id = UUID.randomUUID();
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.traceId = traceId;
		this.details = Map.copyOf(details);
	}
}
