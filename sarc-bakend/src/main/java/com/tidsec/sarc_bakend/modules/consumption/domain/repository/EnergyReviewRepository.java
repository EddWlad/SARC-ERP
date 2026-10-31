package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;

public interface EnergyReviewRepository {
	void insert(EnergyReview review);
	List<EnergyReview> history(UUID sessionId);
	List<EnergyReview> findForSessions(Collection<UUID> sessionIds);
}
