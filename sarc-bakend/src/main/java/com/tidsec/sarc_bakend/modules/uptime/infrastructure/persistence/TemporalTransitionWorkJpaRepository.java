package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
interface TemporalTransitionWorkJpaRepository extends JpaRepository<TemporalTransitionWork,UUID> {
    Optional<TemporalTransitionWork> findByRequestedByAndRequestKey(UUID actor,UUID key);
    List<TemporalTransitionWork> findByStatusIn(Collection<TemporalTransitionWork.Status> statuses);
}
