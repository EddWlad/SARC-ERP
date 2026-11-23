package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Comunicacion publicada a mano: el canal, el tipo y la hora real del envio que informa el analista (FR-058). Copiar el
 * texto del mensaje no crea ninguna fila (FR-059): solo existe lo que el analista confirma. Nunca se borra; si se
 * registro por error se anula con motivo y sigue visible.
 */
@Entity
@Table(name = "inc_communication")
public class Communication {

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ticketId;

	@Column(nullable = false, updatable = false)
	private UUID episodeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private CommunicationChannel channel;

	@Column(updatable = false)
	private String channelDetail;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private CommunicationKind kind;

	@Column(nullable = false, updatable = false)
	private Instant sentAt;

	@Column(nullable = false, updatable = false)
	private boolean retroactive;

	@Column(updatable = false)
	private String justification;

	@Column(updatable = false)
	private UUID recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	private Instant annulledAt;

	private UUID annulledBy;

	private String annulReason;

	protected Communication() {
	}

	private Communication(UUID ticketId, UUID episodeId, CommunicationChannel channel, String channelDetail,
			CommunicationKind kind, Instant sentAt, boolean retroactive, String justification, UUID recordedBy,
			Instant recordedAt) {
		this.id = UUID.randomUUID();
		this.ticketId = ticketId;
		this.episodeId = episodeId;
		this.channel = channel;
		this.channelDetail = channelDetail;
		this.kind = kind;
		this.sentAt = sentAt;
		this.retroactive = retroactive;
		this.justification = justification;
		this.recordedBy = recordedBy;
		this.recordedAt = recordedAt;
	}

	/** Registra el envio. {@code OTHER} exige detalle y una hora retroactiva exige justificacion (las mismas reglas que la base). */
	public static Communication record(UUID ticketId, UUID episodeId, CommunicationChannel channel, String channelDetail,
			CommunicationKind kind, Instant sentAt, boolean retroactive, String justification, UUID recordedBy,
			Instant recordedAt) {
		if (channel == CommunicationChannel.OTHER && (channelDetail == null || channelDetail.isBlank())) {
			throw new IllegalArgumentException("El canal «Otro» exige indicar cual");
		}
		if (retroactive && (justification == null || justification.isBlank())) {
			throw new IllegalArgumentException("Una hora anterior exige una justificacion");
		}
		return new Communication(ticketId, episodeId, channel, channelDetail, kind, sentAt, retroactive, justification,
				recordedBy, recordedAt);
	}

	/** Anula la comunicacion con un motivo; sigue visible. Anular dos veces no cambia el primer motivo. */
	public void annul(UUID by, Instant at, String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Anular exige un motivo");
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

	public CommunicationChannel getChannel() {
		return channel;
	}

	public String getChannelDetail() {
		return channelDetail;
	}

	public CommunicationKind getKind() {
		return kind;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public boolean isRetroactive() {
		return retroactive;
	}

	public String getJustification() {
		return justification;
	}

	public UUID getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
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
