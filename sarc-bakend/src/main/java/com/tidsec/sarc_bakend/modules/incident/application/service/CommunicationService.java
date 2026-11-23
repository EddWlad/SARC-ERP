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
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationChannel;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Registra el envio real de un aviso (FR-058, FR-059): despues de copiar el mensaje y publicarlo a mano, el analista
 * confirma el canal, el tipo y la hora real. Copiar el texto no crea nada; solo esta confirmacion deja una fila, un
 * evento {@code COMMUNICATION_RECORDED} en la cronologia y la auditoria. Una hora anterior al registro exige
 * justificacion y queda marcada como retroactiva; una futura se rechaza. Anularla se hace con el evento, con motivo.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class CommunicationService {

	private static final int MAX_DETAIL = 150;
	private static final int MAX_JUSTIFICATION = 300;

	private final TicketLoader loader;
	private final CommunicationRepository communications;
	private final TicketEventRepository events;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	CommunicationService(TicketLoader loader, CommunicationRepository communications, TicketEventRepository events,
			AuditTrail audit, CurrentActor actor, Clock clock) {
		this.loader = loader;
		this.communications = communications;
		this.events = events;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	@Transactional
	public void record(UUID ticketId, long version, CommunicationChannel channel, String channelDetail,
			CommunicationKind kind, Instant sentAt, String justification) {
		Ticket ticket = loader.load(ticketId, version);
		String detail = clean(channelDetail, MAX_DETAIL, "El detalle del canal admite hasta " + MAX_DETAIL + " caracteres");
		if (channel == CommunicationChannel.OTHER && detail == null) {
			throw IncidentErrors.eventInvalid("El canal «Otro» exige indicar cuál");
		}
		String why = clean(justification, MAX_JUSTIFICATION,
				"La justificación admite hasta " + MAX_JUSTIFICATION + " caracteres");
		Instant now = Instant.now(clock);
		RetroactivePolicy.Kind timing = RetroactivePolicy.evaluate(sentAt, now);
		if (timing == RetroactivePolicy.Kind.FUTURE) {
			throw IncidentErrors.eventInvalid("La hora del envío no puede estar en el futuro");
		}
		boolean retroactive = timing == RetroactivePolicy.Kind.RETROACTIVE;
		if (retroactive && why == null) {
			throw IncidentErrors.justificationRequired("Una hora anterior al registro exige una justificación");
		}
		UUID author = actor.id().orElse(null);
		Communication saved = communications.save(Communication.record(ticketId, ticket.latestEpisode().getId(), channel,
				channel == CommunicationChannel.OTHER ? detail : null, kind, sentAt, retroactive,
				retroactive ? why : null, author, now));
		Map<String, Object> details = new HashMap<>();
		details.put("communicationId", saved.getId().toString());
		details.put("channel", channel.name());
		details.put("kind", kind.name());
		if (saved.getChannelDetail() != null) {
			details.put("channelDetail", saved.getChannelDetail());
		}
		events.save(TicketEvent.record(ticketId, ticket.latestEpisode().getId(), EventType.COMMUNICATION_RECORDED, sentAt,
				now, author, retroactive, retroactive ? why : null, null, null, details, false));
		ticket.touch(now);
		audit.record("TICKET_COMMUNICATION_RECORDED", "INCIDENT_TICKET", ticketId, Map.of("code",
				new TicketCode(ticket.getTicketNumber()).text(), "channel", channel.name(), "kind", kind.name()));
	}

	private static String clean(String text, int max, String message) {
		if (text == null || text.isBlank()) {
			return null;
		}
		if (text.strip().length() > max) {
			throw IncidentErrors.eventInvalid(message);
		}
		return text.strip();
	}
}
