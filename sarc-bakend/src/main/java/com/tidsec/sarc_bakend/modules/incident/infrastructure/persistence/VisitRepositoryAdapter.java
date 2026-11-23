package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Visit;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.VisitRepository;

@Component
class VisitRepositoryAdapter implements VisitRepository {

	private final VisitJpaRepository jpa;

	VisitRepositoryAdapter(VisitJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Visit save(Visit visit) {
		return jpa.save(visit);
	}

	@Override
	public Optional<Visit> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<Visit> findByTicket(UUID ticketId) {
		return jpa.findByTicketIdOrderByCreatedAtAsc(ticketId);
	}

	/** Por lotes de mil tickets, para no pasar el tope de parametros de una consulta. */
	@Override
	public List<Visit> findByTickets(java.util.Collection<UUID> ticketIds) {
		List<UUID> ids = List.copyOf(ticketIds);
		List<Visit> found = new java.util.ArrayList<>();
		for (int from = 0; from < ids.size(); from += 1000) {
			found.addAll(jpa.findByTicketIdIn(ids.subList(from, Math.min(from + 1000, ids.size()))));
		}
		return found;
	}

	@Override
	public List<String> providerNames(String search, int limit) {
		String text = search == null ? "" : search.strip().toLowerCase();
		return jpa.providerNames(escapeLike(text), PageRequest.of(0, limit));
	}

	/** Los comodines de LIKE escritos por el usuario se buscan como texto literal. */
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}

interface VisitJpaRepository extends JpaRepository<Visit, UUID> {

	List<Visit> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

	List<Visit> findByTicketIdIn(java.util.Collection<UUID> ticketIds);

	@Query("""
			select distinct v.providerName from Visit v
			where v.providerName is not null and lower(v.providerName) like concat('%', :search, '%') escape '\\'
			order by v.providerName""")
	List<String> providerNames(String search, PageRequest page);
}
