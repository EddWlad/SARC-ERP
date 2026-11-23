package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Evidence;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.EvidenceRepository;

@Component
class EvidenceRepositoryAdapter implements EvidenceRepository {

	private final EvidenceJpaRepository jpa;

	EvidenceRepositoryAdapter(EvidenceJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Evidence save(Evidence evidence) {
		return jpa.save(evidence);
	}

	@Override
	public Optional<Evidence> findById(UUID id) {
		return jpa.findById(id);
	}

	@Override
	public List<Evidence> findByTicket(UUID ticketId) {
		return jpa.findByTicketIdOrderByUploadedAtAsc(ticketId);
	}
}

interface EvidenceJpaRepository extends JpaRepository<Evidence, UUID> {

	List<Evidence> findByTicketIdOrderByUploadedAtAsc(UUID ticketId);
}
