package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.application.command.CloseTicketCommand;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureCheck;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureData;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Evidence;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidencePurpose;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ClosureRequirements;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.EvidenceRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.MilestoneRevisionRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Cierre y reapertura de un ticket (FR-044, FR-047 a FR-050). Solo editores. Cerrar valida los requisitos de la clase del
 * elemento y, si falta alguno, responde 422 {@code TICKET_CLOSE_REQUIREMENTS} con la lista de lo que falta, sin cambiar
 * nada. Un cierre valido registra T4, guarda el resultado y la causa, y deja un evento {@code TICKET_CLOSED}; reabrir crea
 * un episodio nuevo con su propio T0 y la version de referencias vigente, y deja {@code TICKET_REOPENED}. Ambos eventos
 * alimentan la campana futura (FR-096). Cada operacion lleva la version que leyo el cliente.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class TicketLifecycleService {

	private static final int MAX_CAUSE = 500;
	private static final int MAX_RECOMMENDATIONS = 1000;
	private static final int MAX_WAIVER = 300;
	private static final int MAX_DEVIATION = 500;

	private final TicketLoader loader;
	private final TicketRepository tickets;
	private final TicketEventRepository events;
	private final MilestoneRevisionRepository revisions;
	private final EvidenceRepository evidence;
	private final CommunicationRepository communications;
	private final ReferenceCatalog references;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	TicketLifecycleService(TicketLoader loader, TicketRepository tickets, TicketEventRepository events,
			MilestoneRevisionRepository revisions, EvidenceRepository evidence, CommunicationRepository communications,
			ReferenceCatalog references, AuditTrail audit, CurrentActor actor, Clock clock) {
		this.loader = loader;
		this.tickets = tickets;
		this.events = events;
		this.revisions = revisions;
		this.evidence = evidence;
		this.communications = communications;
		this.references = references;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	@Transactional
	public void close(CloseTicketCommand command) {
		Ticket ticket = loader.load(command.ticketId(), command.version());
		Episode episode = ticket.latestEpisode();
		if (!episode.isOpen()) {
			throw IncidentErrors.alreadyClosed();
		}
		validateLengths(command);
		Instant now = Instant.now(clock);
		Instant cutOff = command.t4() == null ? now : command.t4();
		List<TicketEvent> timeline = events.findByTicket(ticket.getId());

		List<String> missing = ClosureRequirements.missing(inputFor(ticket, episode, command, timeline, cutOff));
		if (!missing.isEmpty()) {
			throw IncidentErrors.closeRequirements(missing);
		}
		Optional<Ticket> original = Optional.ofNullable(command.duplicateOfTicketId()).flatMap(tickets::findById);
		if (command.result() == ClosureResult.DUPLICATE
				&& (original.isEmpty() || original.get().getId().equals(ticket.getId()))) {
			throw IncidentErrors.elementInvalid("El ticket original no existe o es el mismo ticket");
		}

		UUID author = actor.id().orElse(null);
		MilestoneRevision t4 = ticket.close(new ClosureData(command.t4(), blankToNull(command.t4Reason()), command.result(),
				command.duplicateOfTicketId(), command.causeKind(), blankToNull(command.causeText()),
				blankToNull(command.recommendations()), checks(ticket, command), blankToNull(command.finalEvidenceWaiver()),
				blankToNull(command.closureCommunicationWaiver()), blankToNull(command.deviationJustification())), author,
				now);
		revisions.save(t4);

		Map<String, Object> details = new HashMap<>();
		details.put("episode", episode.getOrdinal());
		details.put("result", command.result().name());
		details.put("causeKind", command.causeKind().name());
		original.ifPresent(found -> details.put("duplicateOfCode", new TicketCode(found.getTicketNumber()).text()));
		events.save(TicketEvent.record(ticket.getId(), episode.getId(), EventType.TICKET_CLOSED,
				episode.milestone(MilestoneKind.T4).getOccurredAt(), now, author,
				episode.milestone(MilestoneKind.T4).isRetroactive(), blankToNull(command.recommendations()), null, null,
				details, EventType.TICKET_CLOSED.isNotifiable(null)));
		audit.record("TICKET_CLOSED", "INCIDENT_TICKET", ticket.getId(), Map.of("code",
				new TicketCode(ticket.getTicketNumber()).text(), "result", command.result().name(), "episode",
				Integer.toString(episode.getOrdinal())));
	}

	/**
	 * Reabre el ticket: el caso continua. {@code t0} es la hora de la nueva deteccion (la actual si es nula).
	 */
	@Transactional
	public void reopen(UUID ticketId, long version, String reason, Instant t0, String t0Reason) {
		Ticket ticket = loader.load(ticketId, version);
		Instant now = Instant.now(clock);
		Instant detection = t0 == null ? now : t0;
		ReferenceVersion current = references.versionAt(detection);
		UUID author = actor.id().orElse(null);
		Episode next = ticket.reopen(reason, detection, blankToNull(t0Reason), current.getId(), author, now);
		// El episodio nuevo se persiste ya: el evento que lo nombra lo referencia con una clave foranea.
		tickets.save(ticket);

		Map<String, Object> details = new HashMap<>();
		details.put("episode", next.getOrdinal());
		details.put("t0", detection.toString());
		events.save(TicketEvent.record(ticketId, next.getId(), EventType.TICKET_REOPENED, detection, now, author,
				next.milestone(MilestoneKind.T0).isRetroactive(), reason.strip(), null, null, details,
				EventType.TICKET_REOPENED.isNotifiable(null)));
		audit.record("TICKET_REOPENED", "INCIDENT_TICKET", ticketId, Map.of("code",
				new TicketCode(ticket.getTicketNumber()).text(), "episode", Integer.toString(next.getOrdinal())));
	}

	private ClosureRequirements.Input inputFor(Ticket ticket, Episode episode, CloseTicketCommand command,
			List<TicketEvent> timeline, Instant cutOff) {
		Map<MilestoneKind, MilestoneStatus> statuses = new EnumMap<>(MilestoneKind.class);
		episode.milestonesByKind().forEach((kind, milestone) -> statuses.put(kind, milestone.getStatus()));

		Map<UUID, TicketEvent> byId = timeline.stream().collect(Collectors.toMap(TicketEvent::getId, Function.identity()));
		List<Evidence> active = evidence.findByTicket(ticket.getId()).stream().filter(item -> {
			TicketEvent event = byId.get(item.getEventId());
			return event != null && !event.isAnnulled() && episode.getId().equals(event.getEpisodeId());
		}).toList();
		boolean hasClosureCommunication = communications.findByTicket(ticket.getId()).stream()
				.anyMatch(item -> !item.isAnnulled() && episode.getId().equals(item.getEpisodeId())
						&& item.getKind() == CommunicationKind.CLOSURE);

		boolean outside = ReferenceEvaluator
				.evaluate(episode, references.ruleFor(episode.getReferenceVersionId(), ticket.getIncidentType()), timeline,
						cutOff)
				.stream().anyMatch(judgment -> judgment.result() == ReferenceEvaluator.Result.OUTSIDE);
		return new ClosureRequirements.Input(ticket.getElementClass(), cutOff, statuses, command.result(),
				command.duplicateOfTicketId(), command.causeKind(), command.causeText(), command.closureChecks(),
				active.stream().anyMatch(item -> item.getPurpose() == EvidencePurpose.FINAL),
				command.finalEvidenceWaiver(), !active.isEmpty(), hasClosureCommunication,
				command.closureCommunicationWaiver(), outside, command.deviationJustification());
	}

	/** Las confirmaciones solo se guardan en un equipo y solo las cuatro conocidas. */
	private static Map<String, String> checks(Ticket ticket, CloseTicketCommand command) {
		Map<String, String> stored = new LinkedHashMap<>();
		if (ticket.getElementClass() == ElementClass.EQUIPMENT && command.closureChecks() != null) {
			for (String key : ClosureRequirements.CHECK_KEYS) {
				ClosureCheck value = command.closureChecks().get(key);
				if (value != null) {
					stored.put(key, value.name());
				}
			}
		}
		return stored;
	}

	private static void validateLengths(CloseTicketCommand command) {
		requireMax(command.causeText(), MAX_CAUSE, "La causa admite hasta " + MAX_CAUSE + " caracteres");
		requireMax(command.recommendations(), MAX_RECOMMENDATIONS,
				"Las recomendaciones admiten hasta " + MAX_RECOMMENDATIONS + " caracteres");
		requireMax(command.finalEvidenceWaiver(), MAX_WAIVER, "La justificación admite hasta " + MAX_WAIVER + " caracteres");
		requireMax(command.closureCommunicationWaiver(), MAX_WAIVER,
				"La justificación admite hasta " + MAX_WAIVER + " caracteres");
		requireMax(command.deviationJustification(), MAX_DEVIATION,
				"La justificación de la desviación admite hasta " + MAX_DEVIATION + " caracteres");
		requireMax(command.t4Reason(), MAX_WAIVER, "El motivo admite hasta " + MAX_WAIVER + " caracteres");
	}

	private static void requireMax(String text, int max, String message) {
		if (text != null && text.strip().length() > max) {
			throw IncidentErrors.eventInvalid(message);
		}
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
