package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketRelation;

/** Puerto de las relaciones entre tickets: una fila por par, visible en ambos sentidos. */
public interface TicketRelationRepository {

	TicketRelation save(TicketRelation relation);

	/** La relacion del par, en cualquier orden. */
	Optional<TicketRelation> findBetween(UUID a, UUID b);

	/** Las relaciones donde participa el ticket, en ambos sentidos. */
	List<TicketRelation> findByTicket(UUID ticketId);

	void delete(TicketRelation relation);
}
