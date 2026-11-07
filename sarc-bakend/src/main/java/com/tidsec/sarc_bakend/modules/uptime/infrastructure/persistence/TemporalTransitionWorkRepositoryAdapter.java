package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Repository;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.TemporalTransitionWorkRepository;
@Repository
class TemporalTransitionWorkRepositoryAdapter implements TemporalTransitionWorkRepository {
    private final TemporalTransitionWorkJpaRepository works;
    TemporalTransitionWorkRepositoryAdapter(TemporalTransitionWorkJpaRepository works){this.works=works;}
    public TemporalTransitionWork save(TemporalTransitionWork work){return works.saveAndFlush(work);}
    public Optional<TemporalTransitionWork> findById(UUID id){return works.findById(id);}
    public Optional<TemporalTransitionWork> findRequest(UUID actor,UUID key){return works.findByRequestedByAndRequestKey(actor,key);}
    public List<TemporalTransitionWork> findInProgress(){return works.findByStatusIn(List.of(TemporalTransitionWork.Status.QUEUED,TemporalTransitionWork.Status.RUNNING));}
}
