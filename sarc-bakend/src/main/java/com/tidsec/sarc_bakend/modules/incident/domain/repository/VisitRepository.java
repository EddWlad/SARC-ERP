package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Visit;

/** Puerto de persistencia de las visitas tecnicas. */
public interface VisitRepository {

	Visit save(Visit visit);

	Optional<Visit> findById(UUID id);

	/** Las visitas de un ticket, de la mas antigua a la mas reciente. */
	List<Visit> findByTicket(UUID ticketId);

	/** Las visitas de varios tickets en una consulta; sin orden garantizado entre tickets. */
	List<Visit> findByTickets(java.util.Collection<UUID> ticketIds);

	/** Nombres de proveedor ya usados que contienen el texto buscado (vacio = todos), sin repetir, por orden alfabetico. */
	List<String> providerNames(String search, int limit);
}
