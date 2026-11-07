package com.tidsec.sarc_bakend.modules.uptime.application.result;
import java.util.UUID;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalTransitionWork;
public record TemporalTransitionResult(UUID id,long version,TemporalTransitionWork.Outcome outcome,UUID eventId) {
    public static TemporalTransitionResult from(TemporalTransitionWork.Result result) {
        return new TemporalTransitionResult(result.id(),result.version(),result.outcome(),result.eventId());
    }
}
