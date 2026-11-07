package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.util.List;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;

public record MeasurementHistory(List<MeasurementRevision> revisions, List<MeasurementExclusion> exclusions,
        List<com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementTemporalChange> temporalChanges) {
    public MeasurementHistory(List<MeasurementRevision> revisions,List<MeasurementExclusion> exclusions){this(revisions,exclusions,List.of());}
}
