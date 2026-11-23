package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;

/** Puerto de persistencia del agregado ticket. Nunca borra: el historial es de solo anexion. */
public interface TicketRepository {

	Ticket save(Ticket ticket);

	Optional<Ticket> findById(UUID id);

	/** Los tickets por identidad, sin orden garantizado. */
	List<Ticket> findAllById(Collection<UUID> ids);

	/** El ticket creado con esa clave de idempotencia, si existe (research R2). */
	Optional<Ticket> findByClientRequestId(UUID clientRequestId);

	/**
	 * Los tickets creados hasta el corte, por numero ascendente, con sus episodios e hitos cargados en lote: la base de
	 * los informes, que reconstruyen lo que se sabia en ese instante.
	 */
	List<Ticket> findCreatedUntil(java.time.Instant cutOff);

	/** Tickets abiertos con la misma clave de duplicado (FR-016, research R4), del mas antiguo al mas reciente. */
	List<Ticket> findOpenByDuplicateKey(String duplicateKey);
}
