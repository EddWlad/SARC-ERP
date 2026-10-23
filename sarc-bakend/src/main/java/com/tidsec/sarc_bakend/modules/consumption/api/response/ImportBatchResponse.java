package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportBatch;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportOrigin;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowCounts;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;

import io.swagger.v3.oas.annotations.media.Schema;

public record ImportBatchResponse(
		UUID id,
		SessionSource source,
		String scope,
		ImportOrigin origin,
		String fileName,
		String checksum,
		ImportStatus status,
		@Schema(nullable = true) String failureCode,
		@Schema(nullable = true) String failureMessage,
		int totalRows,
		int newRows,
		int identicalRows,
		int modifiedRows,
		int invalidRows,
		int duplicateRows,
		int pendingLinkRows,
		int appliedRows,
		Instant createdAt,
		@Schema(nullable = true) Instant confirmedAt,
		long version) {

	public static ImportBatchResponse from(ImportBatch batch) {
		RowCounts counts = batch.getCounts();
		return new ImportBatchResponse(batch.getId(), batch.getSource(), batch.getSourceScope(), batch.getOrigin(),
				batch.getFileName(), batch.getChecksum(), batch.getStatus(), batch.getFailureCode(),
				batch.getFailureMessage(), counts.total(), counts.newRows(), counts.identical(), counts.modified(),
				counts.invalid(), counts.duplicate(), counts.pendingLink(), batch.getAppliedRows(),
				batch.getCreatedAt(), batch.getConfirmedAt(), batch.getVersion());
	}
}
