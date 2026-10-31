package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyReviewRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
class EnergyReviewRepositoryAdapter implements EnergyReviewRepository {
	private final EnergyReviewJpaRepository jpa;
	@PersistenceContext private EntityManager entityManager;

	EnergyReviewRepositoryAdapter(EnergyReviewJpaRepository jpa) { this.jpa = jpa; }

	@Override
	public void insert(EnergyReview review) { entityManager.persist(review); }

	@Override
	public List<EnergyReview> history(UUID sessionId) { return jpa.findBySessionIdOrderBySessionVersionDesc(sessionId); }

	@Override
	public List<EnergyReview> findForSessions(Collection<UUID> sessionIds) {
		List<UUID> ids = List.copyOf(sessionIds);
		List<EnergyReview> found = new ArrayList<>();
		for (int start = 0; start < ids.size(); start += 1000) {
			found.addAll(jpa.findBySessionIdInOrderBySessionVersionDesc(ids.subList(start, Math.min(start + 1000, ids.size()))));
		}
		return found;
	}
}
