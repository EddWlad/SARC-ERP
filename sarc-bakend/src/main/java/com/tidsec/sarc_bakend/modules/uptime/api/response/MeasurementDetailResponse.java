package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tidsec.sarc_bakend.modules.uptime.application.result.MeasurementDetail;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Measurement;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementRevision;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReviewState;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.shared.catalog.MeasurableConnector;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle con identificacion vigente y etiqueta de la fecha del hecho (D12). Observaciones y
 * justificacion solo viajan a roles operativos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeasurementDetailResponse(UUID id, long version, UUID connectorId, UUID equipmentId, int ordinal,
		String equipmentName, @Schema(nullable = true) String assignedCode, Instant periodStart, Instant periodEnd,
		int revision, ReadingClass readingClass, PeriodType periodType, ReviewState reviewState,
		@Schema(nullable = true) Integer availableMinutes, @Schema(nullable = true) Integer occupiedMinutes,
		@Schema(nullable = true) Integer faultMinutes, @Schema(nullable = true) Integer offlineMinutes,
		@Schema(nullable = true) Long evaluatedMinutes, @Schema(nullable = true) String uptimePercent, FactLabel factLabel,
		@Schema(nullable = true) List<Observation> observations, @Schema(nullable = true) Boolean validatable,
		@Schema(nullable = true) String reportedPercent, @Schema(nullable = true) String justification,
		@Schema(nullable = true) Boolean excluded, @Schema(nullable = true) Integer effectiveRevision,
		UptimeBasis uptimeBasis) {

	public record FactLabel(String equipmentName, @Schema(nullable = true) String assignedCode, String connectorType) {
	}

	public static MeasurementDetailResponse from(MeasurementDetail detail) {
		Measurement measurement = detail.view().measurement();
		MeasurementRevision revision = detail.view().revision();
		MeasurableConnector connector = detail.connector();
		boolean operational = detail.operational();
		BigDecimal uptime = revision.uptimePercent();
		return new MeasurementDetailResponse(measurement.getId(), measurement.getVersion(), measurement.getConnectorId(),
				measurement.getEquipmentId(), connector.ordinal(), connector.equipmentName(), connector.assignedCode(),
				measurement.getPeriodStart(), measurement.getPeriodEnd(), revision.getRevision(),
				revision.getReadingClass(), revision.getPeriodType(), revision.getReviewState(),
				revision.getAvailableMinutes(), revision.getOccupiedMinutes(), revision.getFaultMinutes(),
				revision.getOfflineMinutes(), revision.evaluatedMinutes(), uptime == null ? null : uptime.toPlainString(),
				new FactLabel(revision.getEquipmentLabel(), revision.getEquipmentCode(), revision.getConnectorTypeLabel()),
				operational ? revision.getObservations() : null, operational ? revision.isValidatable() : null,
				operational && revision.getReportedPercent() != null
						? revision.getReportedPercent().stripTrailingZeros().toPlainString() : null,
				operational ? revision.getJustification() : null, operational ? measurement.isExcluded() : null,
				operational ? measurement.getEffectiveRevision() : null, revision.getUptimeBasis());
	}
}
