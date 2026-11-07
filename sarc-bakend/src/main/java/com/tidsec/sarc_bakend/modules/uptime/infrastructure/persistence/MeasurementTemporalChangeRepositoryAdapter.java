package com.tidsec.sarc_bakend.modules.uptime.infrastructure.persistence;
import java.util.*;
import org.springframework.stereotype.Component;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementTemporalChange;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementTemporalChangeRepository;

@Component
class MeasurementTemporalChangeRepositoryAdapter implements MeasurementTemporalChangeRepository {
    private final MeasurementTemporalChangeJpaRepository events;
    MeasurementTemporalChangeRepositoryAdapter(MeasurementTemporalChangeJpaRepository events){this.events=events;}
    public MeasurementTemporalChange append(MeasurementTemporalChange event){return events.saveAndFlush(event);}
    public Optional<MeasurementTemporalChange> findDecision(UUID measurementId,long version,String policy){
        return events.findByMeasurementIdAndExpectedMeasurementVersionAndPolicyVersion(measurementId,version,policy);
    }
    public List<MeasurementTemporalChange> history(UUID measurementId){
        return events.findByMeasurementIdOrderByOccurredAtDesc(measurementId);
    }
}
