package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestoneOrderPolicy;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy;
import com.tidsec.sarc_bakend.shared.persistence.BaseAuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Ciclo de atencion dentro de un ticket (FR-044). El primero se abre con el ticket y cada reapertura abre otro con su
 * propio T0 y sus propios T1 a T4; los anteriores quedan intactos. Cada episodio guarda la version de referencias
 * vigente en su T0 (FR-051a).
 */
@Entity
@Table(name = "inc_episode")
public class Episode extends BaseAuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", nullable = false, updatable = false)
	private Ticket ticket;

	@Column(nullable = false, updatable = false)
	private short ordinal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TicketState state;

	@Column(nullable = false, updatable = false)
	private UUID referenceVersionId;

	private String reopenReason;

	@Enumerated(EnumType.STRING)
	private ClosureResult closureResult;

	private UUID duplicateOfTicketId;

	@Enumerated(EnumType.STRING)
	private CauseKind causeKind;

	private String causeText;

	private String recommendations;

	private String finalEvidenceWaiver;

	private String closureCommunicationWaiver;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private Map<String, String> closureChecks;

	private String deviationJustification;

	@OneToMany(mappedBy = "episode", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
	@OrderBy("kind ASC")
	@BatchSize(size = 500)
	private List<Milestone> milestones = new ArrayList<>();

	protected Episode() {
	}

	private Episode(Ticket ticket, int ordinal, UUID referenceVersionId, String reopenReason) {
		super(UUID.randomUUID());
		this.ticket = ticket;
		this.ordinal = (short) ordinal;
		this.state = TicketState.OPEN;
		this.referenceVersionId = referenceVersionId;
		this.reopenReason = reopenReason;
	}

	/**
	 * Abre un episodio con sus cinco hitos: T0 registrado con la hora informada y T1 a T4 pendientes (FR-040).
	 * {@code reopenReason} solo aplica a una reapertura (ordinal mayor que 1).
	 */
	static Episode open(Ticket ticket, int ordinal, UUID referenceVersionId, String reopenReason, Instant t0,
			boolean t0Retroactive, String t0Reason, UUID actorId, Instant now) {
		Episode episode = new Episode(ticket, ordinal, referenceVersionId, reopenReason);
		episode.milestones.add(Milestone.recordedT0(episode, t0, t0Retroactive, t0Reason, actorId, now));
		for (MilestoneKind kind : new MilestoneKind[] { MilestoneKind.T1, MilestoneKind.T2, MilestoneKind.T3,
				MilestoneKind.T4 }) {
			episode.milestones.add(Milestone.pending(episode, kind, now));
		}
		return episode;
	}

	public Ticket getTicket() {
		return ticket;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public TicketState getState() {
		return state;
	}

	public boolean isOpen() {
		return state == TicketState.OPEN;
	}

	public UUID getReferenceVersionId() {
		return referenceVersionId;
	}

	public String getReopenReason() {
		return reopenReason;
	}

	public Optional<ClosureResult> closureResult() {
		return Optional.ofNullable(closureResult);
	}

	public UUID getDuplicateOfTicketId() {
		return duplicateOfTicketId;
	}

	public Optional<CauseKind> causeKind() {
		return Optional.ofNullable(causeKind);
	}

	public String getCauseText() {
		return causeText;
	}

	public String getRecommendations() {
		return recommendations;
	}

	public String getFinalEvidenceWaiver() {
		return finalEvidenceWaiver;
	}

	public String getClosureCommunicationWaiver() {
		return closureCommunicationWaiver;
	}

	public Map<String, String> getClosureChecks() {
		return closureChecks == null ? Map.of() : Map.copyOf(closureChecks);
	}

	public String getDeviationJustification() {
		return deviationJustification;
	}

	/** Los cinco hitos, de T0 a T4. */
	public List<Milestone> getMilestones() {
		return List.copyOf(milestones);
	}

	/**
	 * Registra un hito pendiente (FR-041, FR-042): con hora ({@code RECORDED}, la actual si no se indica), o como «No
	 * aplica» o «Sin dato», estos dos con motivo. Una hora mas de un minuto anterior al registro es retroactiva y exige
	 * un motivo. Anexa una {@link MilestoneRevision}. Solo T1 a T3: T4 se registra al cerrar (FR-047).
	 */
	public MilestoneRevision recordMilestone(MilestoneKind kind, MilestoneStatus status, Instant occurredAt,
			String reason, String justification, boolean orderWarningAck, UUID by, Instant now) {
		if (milestone(kind).getStatus() != MilestoneStatus.PENDING) {
			throw IncidentErrors.milestoneInvalid("El hito ya tiene valor: se corrige con una justificación");
		}
		return apply(kind, status, occurredAt, reason, justification, orderWarningAck, by, now);
	}

	/** Corrige un hito que ya tenia valor: exige justificacion, conserva el valor anterior y nunca lo modifica (FR-036). */
	public MilestoneRevision correctMilestone(MilestoneKind kind, MilestoneStatus status, Instant occurredAt,
			String reason, String justification, boolean orderWarningAck, UUID by, Instant now) {
		if (justification == null || justification.isBlank()) {
			throw IncidentErrors.milestoneReasonRequired("Corregir un hito exige una justificación");
		}
		return apply(kind, status, occurredAt, reason, justification, orderWarningAck, by, now);
	}

	private MilestoneRevision apply(MilestoneKind kind, MilestoneStatus status, Instant occurredAt, String reason,
			String justification, boolean orderWarningAck, UUID by, Instant now) {
		Milestone current = milestone(kind);
		if (status == MilestoneStatus.PENDING) {
			throw IncidentErrors.milestoneInvalid("Un hito no vuelve a pendiente");
		}
		if (kind == MilestoneKind.T4 && status != MilestoneStatus.RECORDED) {
			throw IncidentErrors.milestoneT4NotApplicable();
		}
		if (kind == MilestoneKind.T0 && status != MilestoneStatus.RECORDED) {
			throw IncidentErrors.milestoneInvalid("T0 siempre tiene hora");
		}
		if (kind == MilestoneKind.T4 && isOpen()) {
			throw IncidentErrors.episodeNotEditable("T4 se registra al cerrar el ticket");
		}
		boolean editsClosed = !isOpen() && current.getStatus() == MilestoneStatus.PENDING;
		if ((!isOpen() && kind != MilestoneKind.T4 || editsClosed) && (justification == null || justification.isBlank())) {
			throw IncidentErrors.episodeNotEditable("El episodio está cerrado: cambiar T1 a T3 exige una justificación");
		}
		Instant when = null;
		boolean retroactive = false;
		boolean ordered = false;
		String storedReason = reason == null || reason.isBlank() ? null : reason.strip();
		if (status == MilestoneStatus.RECORDED) {
			when = occurredAt == null ? now : occurredAt;
			Map<MilestoneKind, Instant> others = new HashMap<>();
			milestonesByKind().forEach((other, milestone) -> {
				if (other != kind && milestone.getStatus() == MilestoneStatus.RECORDED) {
					others.put(other, milestone.getOccurredAt());
				}
			});
			ordered = MilestoneOrderPolicy.check(kind, when, now, others, orderWarningAck);
			retroactive = RetroactivePolicy.evaluate(when, now) == RetroactivePolicy.Kind.RETROACTIVE;
			if (retroactive && storedReason == null) {
				throw IncidentErrors.milestoneReasonRequired("Una hora anterior al registro exige un motivo");
			}
		}
		else if (storedReason == null) {
			throw IncidentErrors.milestoneReasonRequired("«No aplica» y «Sin dato» exigen un motivo");
		}
		MilestoneRevision revision = new MilestoneRevision(getId(), kind,
				current.getStatus() == MilestoneStatus.PENDING && current.getOccurredAt() == null ? MilestoneStatus.PENDING
						: current.getStatus(),
				current.getOccurredAt(), current.getReason(), status, when, storedReason,
				justification == null || justification.isBlank() ? null : justification.strip(), by, now);
		current.change(status, when, storedReason, retroactive, ordered, by, now);
		return revision;
	}

	/**
	 * Cierra el episodio (FR-047): registra T4 con la hora informada, la actual si no se indica, y guarda el resultado, la
	 * causa y las confirmaciones. T4 no puede ser anterior a ningun hito registrado ni estar en el futuro, y una hora
	 * anterior al registro exige un motivo y queda marcada como retroactiva (FR-041). Los requisitos de cierre los valida
	 * {@code ClosureRequirements} antes de llamar aqui. Devuelve la revision de T4, que el caso de uso guarda.
	 */
	MilestoneRevision close(ClosureData data, UUID by, Instant now) {
		if (!isOpen()) {
			throw IncidentErrors.alreadyClosed();
		}
		Instant when = data.t4() == null ? now : data.t4();
		Map<MilestoneKind, Instant> others = new HashMap<>();
		milestonesByKind().forEach((other, milestone) -> {
			if (other != MilestoneKind.T4 && milestone.getStatus() == MilestoneStatus.RECORDED) {
				others.put(other, milestone.getOccurredAt());
			}
		});
		MilestoneOrderPolicy.check(MilestoneKind.T4, when, now, others, false);
		boolean retroactive = RetroactivePolicy.evaluate(when, now) == RetroactivePolicy.Kind.RETROACTIVE;
		String reason = data.t4Reason() == null || data.t4Reason().isBlank() ? null : data.t4Reason().strip();
		if (retroactive && reason == null) {
			throw IncidentErrors.milestoneReasonRequired("Una hora de cierre anterior al registro exige un motivo");
		}
		Milestone t4 = milestone(MilestoneKind.T4);
		MilestoneRevision revision = new MilestoneRevision(getId(), MilestoneKind.T4, MilestoneStatus.PENDING, null, null,
				MilestoneStatus.RECORDED, when, retroactive ? reason : null, null, by, now);
		t4.change(MilestoneStatus.RECORDED, when, retroactive ? reason : null, retroactive, false, by, now);
		this.closureResult = data.result();
		this.duplicateOfTicketId = data.result() == ClosureResult.DUPLICATE ? data.duplicateOfTicketId() : null;
		this.causeKind = data.causeKind();
		this.causeText = data.causeKind().needsDescription() ? data.causeText() : null;
		this.recommendations = data.recommendations();
		this.finalEvidenceWaiver = data.finalEvidenceWaiver();
		this.closureCommunicationWaiver = data.closureCommunicationWaiver();
		this.closureChecks = data.closureChecks() == null || data.closureChecks().isEmpty() ? null
				: new java.util.LinkedHashMap<>(data.closureChecks());
		this.deviationJustification = data.deviationJustification();
		this.state = TicketState.CLOSED;
		return revision;
	}

	public Milestone milestone(MilestoneKind kind) {
		return milestones.stream().filter(milestone -> milestone.getKind() == kind).findFirst()
				.orElseThrow(() -> new IllegalStateException("El episodio no tiene el hito " + kind));
	}

	/** Los hitos por tipo, en orden. */
	public Map<MilestoneKind, Milestone> milestonesByKind() {
		Map<MilestoneKind, Milestone> byKind = new EnumMap<>(MilestoneKind.class);
		milestones.forEach(milestone -> byKind.put(milestone.getKind(), milestone));
		return byKind;
	}
}
