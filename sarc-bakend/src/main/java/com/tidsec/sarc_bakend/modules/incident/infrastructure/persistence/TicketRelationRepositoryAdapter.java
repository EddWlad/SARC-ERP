package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketRelation;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketRelationId;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRelationRepository;

@Component
class TicketRelationRepositoryAdapter implements TicketRelationRepository {

	private final TicketRelationJpaRepository jpa;

	TicketRelationRepositoryAdapter(TicketRelationJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public TicketRelation save(TicketRelation relation) {
		return jpa.save(relation);
	}

	@Override
	public Optional<TicketRelation> findBetween(UUID a, UUID b) {
		// El par se guarda en el orden de PostgreSQL (texto del UUID); se busca en los dos sentidos.
		return jpa.findById(new TicketRelationId(a, b)).or(() -> jpa.findById(new TicketRelationId(b, a)));
	}

	@Override
	public List<TicketRelation> findByTicket(UUID ticketId) {
		return jpa.findByTicket(ticketId);
	}

	@Override
	public void delete(TicketRelation relation) {
		jpa.delete(relation);
	}
}

interface TicketRelationJpaRepository extends JpaRepository<TicketRelation, TicketRelationId> {

	@Query("select r from TicketRelation r where r.ticketId = :ticketId or r.relatedTicketId = :ticketId")
	List<TicketRelation> findByTicket(UUID ticketId);
}
