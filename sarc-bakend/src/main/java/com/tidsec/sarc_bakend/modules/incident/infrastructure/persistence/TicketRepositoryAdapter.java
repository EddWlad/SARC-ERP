package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.MilestoneRevisionRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;

/** Adaptadores JPA del agregado ticket, su cronologia y las revisiones de hitos. */
@Component
class TicketRepositoryAdapter implements TicketRepository {

	private final TicketJpaRepository jpa;

	TicketRepositoryAdapter(TicketJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Ticket save(Ticket ticket) {
		return jpa.save(ticket);
	}

	@Override
	public Optional<Ticket> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<Ticket> findAllById(Collection<UUID> ids) {
		return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
	}

	@Override
	public Optional<Ticket> findByClientRequestId(UUID clientRequestId) {
		return jpa.findByClientRequestId(clientRequestId);
	}

	@Override
	public List<Ticket> findCreatedUntil(java.time.Instant cutOff) {
		return jpa.findByCreatedAtLessThanEqualOrderByTicketNumberAsc(cutOff);
	}

	@Override
	public List<Ticket> findOpenByDuplicateKey(String duplicateKey) {
		return jpa.findByDuplicateKeyAndStateOrderByTicketNumberAsc(duplicateKey,
				com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState.OPEN);
	}
}

@Component
class TicketEventRepositoryAdapter implements TicketEventRepository {

	private final TicketEventJpaRepository jpa;

	TicketEventRepositoryAdapter(TicketEventJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public TicketEvent save(TicketEvent event) {
		return jpa.save(event);
	}

	@Override
	public Optional<TicketEvent> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<TicketEvent> findByTicket(UUID ticketId) {
		return jpa.findByTicketIdOrderByOccurredAtAscRecordedAtAsc(ticketId);
	}

	/** Por lotes de mil tickets, para no pasar el tope de parametros de una consulta. */
	/** Por lotes de mil tickets, solo de los tipos pedidos. */
	@Override
	public List<TicketEvent> findByTicketsAndTypes(Collection<UUID> ticketIds,
			Collection<com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType> types) {
		List<UUID> ids = List.copyOf(ticketIds);
		List<TicketEvent> found = new java.util.ArrayList<>();
		for (int from = 0; from < ids.size(); from += 1000) {
			found.addAll(jpa.findByTicketIdInAndTypeIn(ids.subList(from, Math.min(from + 1000, ids.size())), types));
		}
		return found;
	}

	@Override
	public List<TicketEvent> findByTickets(Collection<UUID> ticketIds) {
		List<UUID> ids = List.copyOf(ticketIds);
		List<TicketEvent> found = new java.util.ArrayList<>();
		for (int from = 0; from < ids.size(); from += 1000) {
			found.addAll(jpa.findByTicketIdIn(ids.subList(from, Math.min(from + 1000, ids.size()))));
		}
		return found;
	}
}

@Component
class MilestoneRevisionRepositoryAdapter implements MilestoneRevisionRepository {

	private final MilestoneRevisionJpaRepository jpa;

	MilestoneRevisionRepositoryAdapter(MilestoneRevisionJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public MilestoneRevision save(MilestoneRevision revision) {
		return jpa.save(revision);
	}

	@Override
	public List<MilestoneRevision> findByEpisode(UUID episodeId) {
		return jpa.findByEpisodeIdOrderByRecordedAtAsc(episodeId);
	}

	/** Por lotes de mil identificadores, para no pasar el tope de parametros de una consulta. */
	@Override
	public List<MilestoneRevision> findByEpisodes(Collection<UUID> episodeIds) {
		List<UUID> ids = List.copyOf(episodeIds);
		List<MilestoneRevision> found = new java.util.ArrayList<>();
		for (int from = 0; from < ids.size(); from += 1000) {
			found.addAll(jpa.findByEpisodeIdIn(ids.subList(from, Math.min(from + 1000, ids.size()))));
		}
		return found;
	}
}

interface TicketJpaRepository extends JpaRepository<Ticket, UUID> {

	Optional<Ticket> findByClientRequestId(UUID clientRequestId);

	List<Ticket> findByCreatedAtLessThanEqualOrderByTicketNumberAsc(java.time.Instant cutOff);

	List<Ticket> findByDuplicateKeyAndStateOrderByTicketNumberAsc(String duplicateKey,
			com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState state);
}

interface TicketEventJpaRepository extends JpaRepository<TicketEvent, UUID> {

	List<TicketEvent> findByTicketIdOrderByOccurredAtAscRecordedAtAsc(UUID ticketId);

	List<TicketEvent> findByTicketIdIn(Collection<UUID> ticketIds);

	List<TicketEvent> findByTicketIdInAndTypeIn(Collection<UUID> ticketIds,
			Collection<com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType> types);
}

interface MilestoneRevisionJpaRepository extends JpaRepository<MilestoneRevision, UUID> {

	List<MilestoneRevision> findByEpisodeIdOrderByRecordedAtAsc(UUID episodeId);

	List<MilestoneRevision> findByEpisodeIdIn(Collection<UUID> episodeIds);
}
