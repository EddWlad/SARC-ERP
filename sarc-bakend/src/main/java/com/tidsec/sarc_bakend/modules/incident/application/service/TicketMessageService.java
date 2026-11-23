package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.application.result.MessageView;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Action;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Message;
import com.tidsec.sarc_bakend.modules.incident.domain.message.TicketMessageComposer.Options;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;

/**
 * Texto del mensaje para WhatsApp de un ticket (FR-055 a FR-060). Solo lee: no crea eventos ni comunicaciones, porque
 * copiar el texto no demuestra que se haya enviado (FR-059). Lo pueden pedir los cinco roles. Toma el nombre, la
 * ubicacion y el enlace de Maps <b>actuales</b> del inventario para un equipo y los conservados en T0 para un elemento
 * del sitio, y las acciones solo del episodio vigente.
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
@Transactional(readOnly = true)
public class TicketMessageService {

	private static final Set<EventType> ACTION_TYPES = Set.of(EventType.CORRECTIVE_ACTION, EventType.ESCALATED);

	private final TicketRepository tickets;
	private final TicketEventRepository events;
	private final IncidentAssetDirectory assets;

	TicketMessageService(TicketRepository tickets, TicketEventRepository events, IncidentAssetDirectory assets) {
		this.tickets = tickets;
		this.events = events;
		this.assets = assets;
	}

	public MessageView compose(UUID ticketId, boolean includeDetection, boolean includeActions) {
		Ticket ticket = tickets.findById(ticketId).orElseThrow(IncidentErrors::ticketNotFound);
		Set<UUID> ids = new java.util.HashSet<>();
		if (ticket.getEquipmentId() != null) {
			ids.add(ticket.getEquipmentId());
		}
		if (ticket.getReferenceEquipmentId() != null) {
			ids.add(ticket.getReferenceEquipmentId());
		}
		Map<UUID, IncidentAsset> found = assets.findByIds(ids);
		Episode episode = ticket.latestEpisode();

		String location;
		String maps;
		switch (ticket.getElementClass()) {
			case EQUIPMENT -> {
				IncidentAsset equipment = found.get(ticket.getEquipmentId());
				location = equipment == null ? null : equipment.location();
				maps = equipment == null ? null : equipment.mapsUrl();
			}
			case SITE_ELEMENT -> {
				location = ticket.getLocationAtT0();
				maps = ticket.getMapsUrlAtT0();
			}
			default -> {
				location = null;
				maps = null;
			}
		}
		List<Action> actions = includeActions ? actions(ticketId, episode) : List.of();
		Message message = TicketMessageComposer.compose(new TicketMessageComposer.Input(
				new TicketCode(ticket.getTicketNumber()).text(), ticket.getElementClass(),
				ElementLabelComposer.compose(ticket, found), ticket.getChatbotScope(), location, maps,
				ticket.getDescription(), ticket.getState(), episode.milestone(MilestoneKind.T0).getOccurredAt(), actions),
				new Options(includeDetection, includeActions));
		return new MessageView(message.text(), message.missingFields(), new TicketCode(ticket.getTicketNumber()).text(),
				ticket.getState());
	}

	/** Acciones correctivas y escalamientos no anulados del episodio vigente. */
	private List<Action> actions(UUID ticketId, Episode episode) {
		return events.findByTicket(ticketId).stream()
				.filter(event -> ACTION_TYPES.contains(event.getType()) && !event.isAnnulled()
						&& episode.getId().equals(event.getEpisodeId()))
				.map(TicketMessageService::action).toList();
	}

	private static Action action(TicketEvent event) {
		return new Action(event.getOccurredAt(), event.getText());
	}
}
