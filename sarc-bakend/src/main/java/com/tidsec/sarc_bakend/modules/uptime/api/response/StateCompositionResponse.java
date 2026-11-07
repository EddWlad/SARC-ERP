package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.Map;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.StateComposition;
import io.swagger.v3.oas.annotations.media.Schema;

public record StateCompositionResponse(@Schema(nullable=true) Long availableMinutes,
        @Schema(nullable=true) Long occupiedMinutes,@Schema(nullable=true) Long faultMinutes,
        @Schema(nullable=true) Long offlineMinutes,long totalKnownMinutes,int selectedMeasurements,
        int completeMeasurements,Map<String,Integer> knownMeasurementsByState,boolean isComplete) {
    public static StateCompositionResponse from(StateComposition value) {
        return value==null?null:new StateCompositionResponse(value.availableMinutes(),value.occupiedMinutes(),
            value.faultMinutes(),value.offlineMinutes(),value.totalKnownMinutes(),value.selectedMeasurements(),
            value.completeMeasurements(),value.knownMeasurementsByState(),value.isComplete());
    }
}
