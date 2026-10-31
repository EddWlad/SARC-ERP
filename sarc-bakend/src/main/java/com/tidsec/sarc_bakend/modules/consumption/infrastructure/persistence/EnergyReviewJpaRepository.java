package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;

interface EnergyReviewJpaRepository extends Repository<EnergyReview, UUID> {
	List<EnergyReview> findBySessionIdOrderBySessionVersionDesc(UUID sessionId);
	List<EnergyReview> findBySessionIdInOrderBySessionVersionDesc(Collection<UUID> sessionIds);
}
