package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Visit;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.VisitRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Visitas tecnicas registradas a mano (P-08, FR-035): se solicitan, avanzan por sus etapas y pueden cancelarse o
 * aplazarse con quien decidio, la evidencia y la condicion de nueva escalada. SARC no asigna ni avisa al proveedor.
 * Cada etapa deja un evento {@code VISIT_STAGE} en la cronologia.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class VisitService {

	private static final int MAX_SUGGESTIONS = 20;
	private static final int MAX_PROVIDER = 120;
	private static final int MAX_NOTE = 500;

	private final TicketLoader loader;
	private final VisitRepository visits;
	private final TicketEventRepository events;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	VisitService(TicketLoader loader, VisitRepository visits, TicketEventRepository events, AuditTrail audit,
			CurrentActor actor, Clock clock) {
		this.loader = loader;
		this.visits = visits;
		this.events = events;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	/** Solicita una visita al episodio vigente. El proveedor es opcional: puede conocerse al asignarla. */
	@Transactional
	public void request(UUID ticketId, long version, String providerName, Instant occurredAt, String note) {
		Ticket ticket = loader.load(ticketId, version);
		Visit visit = visits.save(Visit.request(ticketId, ticket.latestEpisode().getId(),
				limited(providerName, MAX_PROVIDER, "El proveedor admite hasta " + MAX_PROVIDER + " caracteres")));
		stageEvent(ticket, visit, null, occurredAt, limited(note, MAX_NOTE, "La nota admite hasta " + MAX_NOTE + " caracteres"),
				"TICKET_VISIT_REQUESTED");
	}

	/** Pasa la visita a otra etapa; las transiciones y los datos de una cancelacion los valida {@link Visit}. */
	@Transactional
	public void moveStage(UUID ticketId, long version, UUID visitId, VisitStage stage, Instant occurredAt, String note,
			String providerName, String decidedBy, String evidenceNote, String reescalationCondition) {
		Ticket ticket = loader.load(ticketId, version);
		Visit visit = visits.findById(visitId).filter(found -> found.getTicketId().equals(ticketId))
				.orElseThrow(IncidentErrors::visitNotFound);
		VisitStage previous = visit.getStage();
		visit.moveTo(stage, limited(providerName, MAX_PROVIDER, "El proveedor admite hasta " + MAX_PROVIDER + " caracteres"),
				limited(decidedBy, 150, "Quien decidió admite hasta 150 caracteres"),
				limited(evidenceNote, MAX_NOTE, "La evidencia admite hasta " + MAX_NOTE + " caracteres"),
				limited(reescalationCondition, 300, "La condición admite hasta 300 caracteres"));
		stageEvent(ticket, visit, previous, occurredAt, limited(note, MAX_NOTE, "La nota admite hasta " + MAX_NOTE + " caracteres"),
				"TICKET_VISIT_STAGE");
	}

	/** Proveedores ya usados en visitas, para sugerirlos al escribir (P-08). Sin duplicados y como maximo 20. */
	@Transactional(readOnly = true)
	public List<String> providers(String search) {
		return visits.providerNames(search, MAX_SUGGESTIONS);
	}

	private void stageEvent(Ticket ticket, Visit visit, VisitStage previous, Instant occurredAt, String note,
			String auditAction) {
		Instant now = Instant.now(clock);
		Instant when = occurredAt == null ? now : occurredAt;
		RetroactivePolicy.Kind kind = RetroactivePolicy.evaluate(when, now);
		if (kind == RetroactivePolicy.Kind.FUTURE) {
			throw IncidentErrors.eventInvalid("La hora de la visita no puede estar en el futuro");
		}
		Map<String, Object> details = new HashMap<>();
		details.put("visitId", visit.getId().toString());
		details.put("stage", visit.getStage().name());
		if (previous != null) {
			details.put("previousStage", previous.name());
		}
		if (visit.getStage().needsDecision()) {
			details.put("decidedBy", visit.getCancelDecidedBy());
			details.put("evidenceNote", visit.getCancelEvidenceNote());
			details.put("reescalationCondition", visit.getReescalationCondition());
		}
		events.save(TicketEvent.record(ticket.getId(), visit.getEpisodeId(), EventType.VISIT_STAGE, when, now,
				actor.id().orElse(null), kind == RetroactivePolicy.Kind.RETROACTIVE, note, null, visit.getProviderName(),
				details, false));
		ticket.touch(now);
		audit.record(auditAction, "INCIDENT_TICKET", ticket.getId(), Map.of("code",
				new TicketCode(ticket.getTicketNumber()).text(), "stage", visit.getStage().name()));
	}

	private static String limited(String text, int max, String message) {
		if (text == null || text.isBlank()) {
			return null;
		}
		if (text.strip().length() > max) {
			throw IncidentErrors.eventInvalid(message);
		}
		return text.strip();
	}
}
