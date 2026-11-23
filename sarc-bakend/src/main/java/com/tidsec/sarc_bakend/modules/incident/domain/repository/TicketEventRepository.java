package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;

/** Puerto de persistencia de la cronologia: anexa eventos y solo permite anular, nunca borrar. */
public interface TicketEventRepository {

	TicketEvent save(TicketEvent event);

	Optional<TicketEvent> findById(UUID id);

	/** La cronologia de un ticket, por hora del hecho y luego por hora de registro. */
	List<TicketEvent> findByTicket(UUID ticketId);

	/** La cronologia de varios tickets en una consulta; sin orden garantizado entre tickets. */
	List<TicketEvent> findByTickets(java.util.Collection<UUID> ticketIds);

	/** Solo los eventos de esos tipos de varios tickets: el tablero no necesita la bitacora completa. */
	List<TicketEvent> findByTicketsAndTypes(java.util.Collection<UUID> ticketIds,
			java.util.Collection<com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType> types);
}
