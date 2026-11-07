package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportRow;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowOutcome;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.Observation;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import io.swagger.v3.oas.annotations.media.Schema;

public record UptimeImportRowResponse(UUID id, UUID batchId, int rowNumber,
		@Schema(nullable = true) String sourceSheet, @Schema(nullable = true) Integer sourceRow, UptimeBasis uptimeBasis,
		@Schema(nullable = true) String sourceSite, @Schema(nullable = true) String sourceCharger,
		@Schema(nullable = true) String sourceConnector, @Schema(nullable = true) UUID equipmentId,
		@Schema(nullable = true) UUID connectorId, @Schema(nullable = true) UUID suggestedConnectorId,
		@Schema(nullable = true) Instant periodStart,
		@Schema(nullable = true) Instant periodEnd, @Schema(nullable = true) PeriodType periodType,
		@Schema(nullable = true) Integer availableMinutes, @Schema(nullable = true) Integer occupiedMinutes,
		@Schema(nullable = true) Integer faultMinutes, @Schema(nullable = true) Integer offlineMinutes,
		@Schema(nullable = true) String reportedPercent, UptimeRowClassification classification, List<RowIssue> issues,
		List<Observation> observations, boolean approved, @Schema(nullable = true) String justification,
		@Schema(nullable = true) UUID existingMeasurementId, @Schema(nullable = true) UptimeRowOutcome outcome,
		@Schema(nullable = true) UUID appliedMeasurementId, long version, TemporalPrecision temporalPrecision, @Schema(nullable=true) LocalDate referenceWeek) {

	public static UptimeImportRowResponse from(UptimeImportRow row) {
		return new UptimeImportRowResponse(row.getId(), row.getBatchId(), row.getRowNumber(), row.getSourceSheet(),
				row.getSourceRow(), row.getUptimeBasis(), row.getSourceSite(),
				row.getSourceCharger(), row.getSourceConnector(), row.getEquipmentId(), row.getConnectorId(),
				row.getSuggestedConnectorId(),row.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?row.getPeriodStart():null,
                row.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?row.getPeriodEnd():null, row.getPeriodType(), row.getAvailableMinutes(),
				row.getOccupiedMinutes(), row.getFaultMinutes(), row.getOfflineMinutes(),
				row.getReportedPercent() == null ? null : row.getReportedPercent().toPlainString(),
				row.getClassification(), row.getIssues(), row.getObservations(), row.isApproved(),
				row.getJustification(), row.getExistingMeasurementId(), row.getOutcome(), row.getAppliedMeasurementId(),
				row.getVersion(),row.getTemporalPrecision(),row.getReferenceWeek());
	}
}
