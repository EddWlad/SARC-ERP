package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.RelationKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketRelation;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRelationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Relaciones entre tickets (FR-017): una falla independiente sobre un equipo con tickets anteriores, o los tickets de
 * otros equipos afectados por la misma falla comun del sitio (un ticket por equipo, FR-003a). Se guarda una sola fila
 * por par, en el orden de PostgreSQL, y se muestra en ambos sentidos. Relacionar no fusiona hitos ni conteos.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class TicketRelationService {

	private final TicketRepository tickets;
	private final TicketRelationRepository relations;
	private final TicketEventRepository events;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	TicketRelationService(TicketRepository tickets, TicketRelationRepository relations, TicketEventRepository events,
			AuditTrail audit, CurrentActor actor, Clock clock) {
		this.tickets = tickets;
		this.relations = relations;
		this.events = events;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	/** Relaciona el ticket con otro. {@code version} es la que leyo el cliente. */
	@Transactional
	public void add(UUID ticketId, long version, UUID relatedTicketId, RelationKind kind, String reason) {
		Ticket ticket = load(ticketId, version);
		if (ticketId.equals(relatedTicketId)) {
			throw IncidentErrors.relationInvalid("Un ticket no se relaciona consigo mismo");
		}
		Ticket related = tickets.findById(relatedTicketId).orElseThrow(IncidentErrors::ticketNotFound);
		if (relations.findBetween(ticketId, relatedTicketId).isPresent()) {
			throw IncidentErrors.relationExists();
		}
		Instant now = Instant.now(clock);
		UUID author = actor.id().orElse(null);
		relations.save(TicketRelation.between(ticketId, relatedTicketId, kind, blankToNull(reason), author, now));
		ticket.touch(now);
		Map<String, Object> details = new HashMap<>();
		details.put("relatedTicketId", relatedTicketId.toString());
		details.put("relatedCode", new TicketCode(related.getTicketNumber()).text());
		details.put("kind", kind.name());
		events.save(TicketEvent.record(ticketId, ticket.latestEpisode().getId(), EventType.RELATION_ADDED, now, now, author,
				false, blankToNull(reason), null, null, details, false));
		audit.record("TICKET_RELATION_ADDED", "INCIDENT_TICKET", ticketId, Map.of("related",
				new TicketCode(related.getTicketNumber()).text(), "kind", kind.name()));
	}

	/** Quita la relacion con otro ticket; queda en la auditoria. */
	@Transactional
	public void remove(UUID ticketId, long version, UUID relatedTicketId) {
		Ticket ticket = load(ticketId, version);
		TicketRelation relation = relations.findBetween(ticketId, relatedTicketId)
				.orElseThrow(() -> IncidentErrors.relationInvalid("Los tickets no están relacionados"));
		relations.delete(relation);
		ticket.touch(Instant.now(clock));
		audit.record("TICKET_RELATION_REMOVED", "INCIDENT_TICKET", ticketId, Map.of("relatedTicketId",
				relatedTicketId.toString()));
	}

	private Ticket load(UUID ticketId, long version) {
		Ticket ticket = tickets.findById(ticketId).orElseThrow(IncidentErrors::ticketNotFound);
		if (!Objects.equals(ticket.getVersion(), version)) {
			throw IncidentErrors.versionConflict();
		}
		return ticket;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
