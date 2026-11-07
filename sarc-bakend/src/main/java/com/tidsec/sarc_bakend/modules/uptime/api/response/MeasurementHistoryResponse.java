package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementHistory;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementExclusion;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReviewState;

import io.swagger.v3.oas.annotations.media.Schema;

/** Historial operativo completo de una medicion. */
public record MeasurementHistoryResponse(List<Revision> revisions, List<Exclusion> exclusions,List<TemporalChange> temporalChanges) {
    public record TemporalChange(UUID eventId,String actorKind,@Schema(nullable=true) UUID actorId,Instant occurredAt,
        String outcome,String reason,java.util.Map<String,Object> previousTemporal,java.util.Map<String,Object> resultTemporal,
        java.util.Map<String,Object> evidence) {
        static TemporalChange from(com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementTemporalChange e) {
            return new TemporalChange(e.getId(),e.getActorKind(),e.getActorId(),e.getOccurredAt(),e.getOutcome().name(),e.getReason(),
                e.getPreviousTemporal(),e.getResultTemporal(),e.getEvidence());
        }
    }

	public record Revision(int revision, ReadingClass readingClass, PeriodType periodType, ReviewState reviewState,
			@Schema(nullable = true) Integer availableMinutes, @Schema(nullable = true) Integer occupiedMinutes,
			@Schema(nullable = true) Integer faultMinutes, @Schema(nullable = true) Integer offlineMinutes,
			@Schema(nullable = true) String uptimePercent, List<Observation> observations,
			@Schema(nullable = true) String justification, MeasurementOrigin origin,
			@Schema(nullable = true) UUID enteredBy, Instant enteredAt, @Schema(nullable = true) UUID validatedBy,
			@Schema(nullable = true) Instant validatedAt, @Schema(nullable = true) String validationJustification,
			@Schema(nullable = true) Instant supersededAt) {

		static Revision from(MeasurementRevision item) {
			return new Revision(item.getRevision(), item.getReadingClass(), item.getPeriodType(), item.getReviewState(),
					item.getAvailableMinutes(), item.getOccupiedMinutes(), item.getFaultMinutes(),
					item.getOfflineMinutes(), item.uptimePercent() == null ? null : item.uptimePercent().toPlainString(),
					item.getObservations(), item.getJustification(), item.getOrigin(), item.getEnteredBy(),
					item.getEnteredAt(), item.getValidatedBy(), item.getValidatedAt(),
					item.getValidationJustification(), item.getSupersededAt());
		}
	}

	public record Exclusion(MeasurementExclusion.Action action, String justification,
			@Schema(nullable = true) UUID decidedBy, Instant decidedAt) {

		static Exclusion from(MeasurementExclusion item) {
			return new Exclusion(item.getAction(), item.getJustification(), item.getDecidedBy(), item.getDecidedAt());
		}
	}

	public static MeasurementHistoryResponse from(MeasurementHistory history) {
		return new MeasurementHistoryResponse(history.revisions().stream().map(Revision::from).toList(),
				history.exclusions().stream().map(Exclusion::from).toList(),history.temporalChanges().stream().map(TemporalChange::from).toList());
	}
}
