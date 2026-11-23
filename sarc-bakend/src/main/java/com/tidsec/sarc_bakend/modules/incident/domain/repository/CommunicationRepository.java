package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;

/** Puerto de persistencia de las comunicaciones: se agregan y solo se anulan, nunca se borran. */
public interface CommunicationRepository {

	Communication save(Communication communication);

	Optional<Communication> findById(UUID id);

	/** Las comunicaciones de un ticket, por hora del envio. */
	List<Communication> findByTicket(UUID ticketId);

	/** Las comunicaciones de varios tickets en una consulta; sin orden garantizado entre tickets. */
	List<Communication> findByTickets(java.util.Collection<UUID> ticketIds);
}
