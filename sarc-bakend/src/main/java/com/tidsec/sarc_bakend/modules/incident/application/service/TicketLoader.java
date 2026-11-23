package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;

/**
 * Carga un ticket para escribir con bloqueo optimista explicito: toda escritura lleva la {@code version} que leyo el
 * cliente y, si otro usuario guardo despues, responde 409 {@code TICKET_VERSION_CONFLICT} en vez de sobrescribir.
 */
@Component
class TicketLoader {

	private final TicketRepository tickets;

	TicketLoader(TicketRepository tickets) {
		this.tickets = tickets;
	}

	Ticket load(UUID id, long version) {
		Ticket ticket = tickets.findById(id).orElseThrow(IncidentErrors::ticketNotFound);
		if (!Objects.equals(ticket.getVersion(), version)) {
			throw IncidentErrors.versionConflict();
		}
		return ticket;
	}
}
