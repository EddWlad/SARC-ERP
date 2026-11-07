package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementView;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReviewState;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;

import io.swagger.v3.oas.annotations.media.Schema;

/** Medicion con sus valores. Porcentajes como texto decimal, sin {@code double}. */
public record MeasurementResponse(UUID id, long version, String result, UUID connectorId, UUID equipmentId,
		MeasurementSource source, @Schema(nullable = true) Instant periodStart, @Schema(nullable = true) Instant periodEnd, String periodZone, int revision,
		ReadingClass readingClass, @Schema(nullable=true) PeriodType periodType, ReviewState reviewState,
		@Schema(nullable = true) Integer availableMinutes, @Schema(nullable = true) Integer occupiedMinutes,
		@Schema(nullable = true) Integer faultMinutes, @Schema(nullable = true) Integer offlineMinutes,
		@Schema(nullable = true) Long evaluatedMinutes, @Schema(nullable = true) String uptimePercent,
		@Schema(nullable = true) String reportedPercent, List<Observation> observations, boolean validatable,
		boolean excluded, UptimeBasis uptimeBasis, TemporalPrecision temporalPrecision,
        @Schema(nullable=true) LocalDate referenceWeek) {

	public static MeasurementResponse from(MeasurementView view, String result) {
		Measurement measurement = view.measurement();
		MeasurementRevision revision = view.revision();
		return new MeasurementResponse(measurement.getId(), measurement.getVersion(), result,
				measurement.getConnectorId(), measurement.getEquipmentId(), measurement.getSource(),
				measurement.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?measurement.getPeriodStart():null,
                measurement.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?measurement.getPeriodEnd():null, measurement.getPeriodZone(),
				revision.getRevision(), revision.getReadingClass(), measurement.effectivePeriodType(revision.getPeriodType()),
				revision.getReviewState(), revision.getAvailableMinutes(), revision.getOccupiedMinutes(),
				revision.getFaultMinutes(), revision.getOfflineMinutes(), measurement.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?revision.evaluatedMinutes():null,
				text(uptime(measurement,revision)), reported(revision.getReportedPercent()), revision.getObservations(),
				revision.isValidatable(), measurement.isExcluded(), revision.getUptimeBasis(), measurement.getTemporalPrecision(), measurement.getReferenceWeek());
	}

    static BigDecimal uptime(Measurement measurement,MeasurementRevision revision) {
        if (measurement.getTemporalPrecision()!=TemporalPrecision.REFERENCE_WEEK) return revision.uptimePercent();
        BigDecimal value=revision.getReportedPercent();
        return value==null || !revision.isValidatable() || revision.getReadingClass()!=ReadingClass.MEASURED
            ?null:value.setScale(2,java.math.RoundingMode.HALF_UP);
    }

	private static String text(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	/** Tal como se informo: la escala de la columna no debe alterar lo que se lee antes y despues de guardar. */
	private static String reported(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}
}
