package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Evidence;

/** Puerto de persistencia de las evidencias: solo se agregan; nunca se borran ni se modifican. */
public interface EvidenceRepository {

	Evidence save(Evidence evidence);

	Optional<Evidence> findById(UUID id);

	/** Las evidencias de un ticket, de la mas antigua a la mas reciente. */
	List<Evidence> findByTicket(UUID ticketId);
}
