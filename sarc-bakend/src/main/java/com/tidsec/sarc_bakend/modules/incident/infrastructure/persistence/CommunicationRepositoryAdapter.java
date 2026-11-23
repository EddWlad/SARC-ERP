package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;

@Component
class CommunicationRepositoryAdapter implements CommunicationRepository {

	private final CommunicationJpaRepository jpa;

	CommunicationRepositoryAdapter(CommunicationJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Communication save(Communication communication) {
		return jpa.save(communication);
	}

	@Override
	public Optional<Communication> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<Communication> findByTicket(UUID ticketId) {
		return jpa.findByTicketIdOrderBySentAtAscRecordedAtAsc(ticketId);
	}

	/** Por lotes de mil tickets, para no pasar el tope de parametros de una consulta. */
	@Override
	public List<Communication> findByTickets(java.util.Collection<UUID> ticketIds) {
		List<UUID> ids = List.copyOf(ticketIds);
		List<Communication> found = new java.util.ArrayList<>();
		for (int from = 0; from < ids.size(); from += 1000) {
			found.addAll(jpa.findByTicketIdIn(ids.subList(from, Math.min(from + 1000, ids.size()))));
		}
		return found;
	}
}

interface CommunicationJpaRepository extends JpaRepository<Communication, UUID> {

	List<Communication> findByTicketIdOrderBySentAtAscRecordedAtAsc(UUID ticketId);

	List<Communication> findByTicketIdIn(java.util.Collection<UUID> ticketIds);
}
