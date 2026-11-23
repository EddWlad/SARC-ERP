package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.MilestoneRevisionRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Registra y corrige hitos (FR-041, FR-042, FR-036) con bloqueo optimista. Cada cambio anexa una revision, deja el
 * evento {@code MILESTONE_RECORDED} o {@code MILESTONE_CORRECTED}, actualiza la ultima actividad y se audita. Un hito
 * pendiente se registra; uno que ya tenia valor se corrige y exige justificacion.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class MilestoneService {

	private final TicketLoader loader;
	private final MilestoneRevisionRepository revisions;
	private final TicketEventRepository events;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	MilestoneService(TicketLoader loader, MilestoneRevisionRepository revisions, TicketEventRepository events,
			AuditTrail audit, CurrentActor actor, Clock clock) {
		this.loader = loader;
		this.revisions = revisions;
		this.events = events;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	/**
	 * @param ordinal el episodio, uno a partir de 1
	 * @param occurredAt la hora del hito; nula es el momento actual (una sola accion, SC-002)
	 */
	@Transactional
	public void apply(UUID ticketId, long version, int ordinal, MilestoneKind kind, MilestoneStatus status,
			Instant occurredAt, String reason, String justification, boolean orderWarningAck) {
		Ticket ticket = loader.load(ticketId, version);
		Episode episode = ticket.episode(ordinal).orElseThrow(IncidentErrors::ticketNotFound);
		Instant now = Instant.now(clock);
		UUID author = actor.id().orElse(null);
		boolean correction = episode.milestone(kind).getStatus() != MilestoneStatus.PENDING;

		MilestoneRevision revision = correction
				? episode.correctMilestone(kind, status, occurredAt, reason, justification, orderWarningAck, author, now)
				: episode.recordMilestone(kind, status, occurredAt, reason, justification, orderWarningAck, author, now);
		revisions.save(revision);

		EventType type = correction ? EventType.MILESTONE_CORRECTED : EventType.MILESTONE_RECORDED;
		Instant eventTime = revision.getNewOccurredAt() != null ? revision.getNewOccurredAt() : now;
		Map<String, Object> details = new HashMap<>();
		details.put("kind", kind.name());
		details.put("status", status.name());
		details.put("episode", ordinal);
		if (correction) {
			details.put("previousStatus", String.valueOf(revision.getPreviousStatus()));
			if (revision.getPreviousOccurredAt() != null) {
				details.put("previousOccurredAt", revision.getPreviousOccurredAt().toString());
			}
		}
		events.save(TicketEvent.record(ticketId, episode.getId(), type, eventTime, now, author,
				episode.milestone(kind).isRetroactive(), justification != null && !justification.isBlank() ? justification
						: reason, null, null, details, false));
		ticket.touch(now);
		audit.record(correction ? "MILESTONE_CORRECTED" : "MILESTONE_RECORDED", "INCIDENT_TICKET", ticketId,
				Map.of("code", new TicketCode(ticket.getTicketNumber()).text(), "milestone", kind.name(), "status",
						status.name(), "episode", Integer.toString(ordinal)));
	}
}
