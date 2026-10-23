package com.tidsec.sarc_bakend.modules.consumption.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;

interface EnergySessionJpaRepository extends JpaRepository<EnergySession, UUID>, JpaSpecificationExecutor<EnergySession> {

	Optional<EnergySession> findBySourceAndSourceScopeAndExternalSessionId(SessionSource source, String sourceScope,
			String externalSessionId);

	List<EnergySession> findBySourceAndSourceScopeAndExternalSessionIdIn(SessionSource source, String sourceScope,
			Collection<String> externalSessionIds);

	@Query("""
			select s from EnergySession s
			where s.state = com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState.PENDING_LINK
			  and (:afterId is null or s.id > :afterId)
			order by s.id""")
	List<EnergySession> findPendingLink(@Param("afterId") UUID afterId, Pageable pageable);
}
