package com.tidsec.sarc_bakend.modules.uptime.domain.repository;
import java.util.*;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementTemporalChange;

/** Persistencia local de decisiones temporales; sin actualizacion/borrado de eventos. */
public interface MeasurementTemporalChangeRepository {
    MeasurementTemporalChange append(MeasurementTemporalChange event);
    Optional<MeasurementTemporalChange> findDecision(UUID measurementId,long originVersion,String policyVersion);
    List<MeasurementTemporalChange> history(UUID measurementId);
}
