package com.tidsec.sarc_bakend.modules.uptime.domain.repository;
import java.util.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
public interface TemporalTransitionWorkRepository {
    TemporalTransitionWork save(TemporalTransitionWork work);
    Optional<TemporalTransitionWork> findById(UUID id);
    Optional<TemporalTransitionWork> findRequest(UUID actor,UUID key);
    List<TemporalTransitionWork> findInProgress();
}
