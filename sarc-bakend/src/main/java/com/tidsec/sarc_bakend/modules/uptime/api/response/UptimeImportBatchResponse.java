package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.time.Instant;
import java.time.LocalDate;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.TemporalPrecision;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeImportBatch;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowCounts;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record UptimeImportBatchResponse(UUID id, MeasurementSource source, PeriodType declaredPeriodType,
		@Schema(nullable = true) Instant declaredPeriodStart, @Schema(nullable = true) Instant declaredPeriodEnd,
		ImportOrigin origin, String fileName, String checksum, ImportStatus status,
		@Schema(nullable = true) String failureCode, @Schema(nullable = true) String failureMessage, int totalRows,
		int newRows, int identicalRows, int conflictRows, int invalidRows, int pendingLinkRows, int observedRows,
		int appliedRows, boolean exactConfirmed, Instant createdAt, @Schema(nullable = true) Instant confirmedAt,
		long version, TemporalPrecision temporalPrecision, @Schema(nullable=true) LocalDate referenceWeek) {

	public static UptimeImportBatchResponse from(UptimeImportBatch batch) {
		UptimeRowCounts counts = batch.getCounts();
		return new UptimeImportBatchResponse(batch.getId(), batch.getSource(), batch.getDeclaredPeriodType(),
				batch.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?batch.getDeclaredPeriodStart():null,
                batch.getTemporalPrecision()==TemporalPrecision.KNOWN_RANGE?batch.getDeclaredPeriodEnd():null, batch.getOrigin(), batch.getFileName(), batch.getChecksum(), batch.getStatus(), batch.getFailureCode(),
				batch.getFailureMessage(), counts.total(), counts.newRows(), counts.identical(), counts.conflict(),
				counts.invalid(), counts.pendingLink(), counts.observed(), batch.getAppliedRows(),
				batch.isExactConfirmed(), batch.getCreatedAt(), batch.getConfirmedAt(), batch.getVersion(),batch.getTemporalPrecision(),batch.getReferenceWeek());
	}
}
