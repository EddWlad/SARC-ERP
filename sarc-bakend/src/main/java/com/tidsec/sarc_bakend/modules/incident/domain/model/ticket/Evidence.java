package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evidencia de un ticket: imagen o PDF con su huella SHA-256. Es inmutable y no se borra: si se cargo por error se
 * anula su evento {@code EVIDENCE_ADDED} y sigue visible en la auditoria (FR-037). El archivo vive en el
 * almacenamiento externo y aqui solo queda su referencia opaca.
 */
@Entity
@Table(name = "inc_evidence")
public class Evidence {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ticketId;

	@Column(updatable = false)
	private UUID eventId;

	@Column(updatable = false)
	private UUID communicationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private EvidencePurpose purpose;

	@Column(nullable = false, updatable = false)
	private String storageRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "format", nullable = false, updatable = false)
	private EvidenceFormat format;

	@Column(nullable = false, updatable = false)
	private long sizeBytes;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", nullable = false, updatable = false, length = 64)
	private String sha256;

	@Column(nullable = false, updatable = false)
	private String originalName;

	@Column(updatable = false)
	private UUID uploadedBy;

	@Column(nullable = false, updatable = false)
	private Instant uploadedAt;

	protected Evidence() {
	}

	private Evidence(UUID id, UUID ticketId, UUID eventId, UUID communicationId, EvidencePurpose purpose, String storageRef,
			EvidenceFormat format, long sizeBytes, String sha256, String originalName, UUID uploadedBy,
			Instant uploadedAt) {
		this.id = id;
		this.ticketId = ticketId;
		this.eventId = eventId;
		this.communicationId = communicationId;
		this.purpose = purpose;
		this.storageRef = storageRef;
		this.format = format;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.originalName = originalName;
		this.uploadedBy = uploadedBy;
		this.uploadedAt = uploadedAt;
	}

	public static Evidence of(UUID id, UUID ticketId, UUID eventId, UUID communicationId, EvidencePurpose purpose,
			String storageRef,
			EvidenceFormat format, long sizeBytes, String sha256, String originalName, UUID uploadedBy,
			Instant uploadedAt) {
		if (sizeBytes <= 0) {
			throw new IllegalArgumentException("Una evidencia no puede estar vacía");
		}
		return new Evidence(id, ticketId, eventId, communicationId, purpose, storageRef, format, sizeBytes, sha256, originalName,
				uploadedBy, uploadedAt);
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getCommunicationId() {
		return communicationId;
	}

	public EvidencePurpose getPurpose() {
		return purpose;
	}

	public String getStorageRef() {
		return storageRef;
	}

	public EvidenceFormat getFormat() {
		return format;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public String getOriginalName() {
		return originalName;
	}

	public UUID getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
