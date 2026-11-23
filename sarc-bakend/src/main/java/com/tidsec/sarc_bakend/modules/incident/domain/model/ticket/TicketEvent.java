package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.Map;
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
 * Entrada fechada de la cronologia (FR-031). Un evento nunca se borra: la unica modificacion permitida es anularlo con
 * un motivo, y sigue visible en la auditoria (FR-036). Lleva la hora del hecho ({@code occurredAt}, que puede ser
 * retroactiva) y la del registro, y sus detalles acotados por tipo, nunca libres desde el cliente.
 */
@Entity
@Table(name = "inc_event")
public class TicketEvent {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ticketId;

	@Column(updatable = false)
	private UUID episodeId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, updatable = false)
	private EventType type;

	@Column(nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	@Column(updatable = false)
	private UUID recordedBy;

	@Column(nullable = false, updatable = false)
	private boolean retroactive;

	@Column(name = "text", updatable = false)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private TargetKind targetKind;

	@Column(updatable = false)
	private String targetRoleOrProvider;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> details;

	@Column(nullable = false, updatable = false)
	private boolean notifiable;

	private Instant annulledAt;

	private UUID annulledBy;

	private String annulReason;

	protected TicketEvent() {
	}

	private TicketEvent(UUID ticketId, UUID episodeId, EventType type, Instant occurredAt, Instant recordedAt,
			UUID recordedBy, boolean retroactive, String text, TargetKind targetKind, String targetRoleOrProvider,
			Map<String, Object> details, boolean notifiable) {
		this.id = UUID.randomUUID();
		this.ticketId = ticketId;
		this.episodeId = episodeId;
		this.type = type;
		this.occurredAt = occurredAt;
		this.recordedAt = recordedAt;
		this.recordedBy = recordedBy;
		this.retroactive = retroactive;
		this.text = text;
		this.targetKind = targetKind;
		this.targetRoleOrProvider = targetRoleOrProvider;
		this.details = details == null || details.isEmpty() ? null : Map.copyOf(details);
		this.notifiable = notifiable;
	}

	/**
	 * Registra un evento. Un escalamiento exige su destino ({@link TargetKind}); solo el destino tecnico cuenta para la
	 * referencia de la Espera de conexion (FR-052).
	 */
	public static TicketEvent record(UUID ticketId, UUID episodeId, EventType type, Instant occurredAt,
			Instant recordedAt, UUID recordedBy, boolean retroactive, String text, TargetKind targetKind,
			String targetRoleOrProvider, Map<String, Object> details, boolean notifiable) {
		if (type == EventType.ESCALATED && targetKind == null) {
			throw new IllegalArgumentException("Un escalamiento exige su destino");
		}
		return new TicketEvent(ticketId, episodeId, type, occurredAt, recordedAt, recordedBy, retroactive, text,
				targetKind, targetRoleOrProvider, details, notifiable);
	}

	/** Anula el evento con un motivo; sigue visible en la cronologia. Anular dos veces no cambia el primer motivo. */
	public void annul(UUID by, Instant at, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Anular un evento exige un motivo");
		}
		if (annulledAt == null) {
			this.annulledAt = at;
			this.annulledBy = by;
			this.annulReason = reason;
		}
	}

	public boolean isAnnulled() {
		return annulledAt != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTicketId() {
		return ticketId;
	}

	public UUID getEpisodeId() {
		return episodeId;
	}

	public EventType getType() {
		return type;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public boolean isRetroactive() {
		return retroactive;
	}

	public String getText() {
		return text;
	}

	public TargetKind getTargetKind() {
		return targetKind;
	}

	public String getTargetRoleOrProvider() {
		return targetRoleOrProvider;
	}

	public Map<String, Object> getDetails() {
		return details == null ? Map.of() : details;
	}

	public boolean isNotifiable() {
		return notifiable;
	}

	public Instant getAnnulledAt() {
		return annulledAt;
	}

	public UUID getAnnulledBy() {
		return annulledBy;
	}

	public String getAnnulReason() {
		return annulReason;
	}
}
