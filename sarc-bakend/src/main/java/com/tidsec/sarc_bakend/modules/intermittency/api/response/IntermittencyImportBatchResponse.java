package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.ImportFormat;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowCounts;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lote de importacion: estado, conteos por clasificacion, rango de dias y huella del original (FR-030). */
public record IntermittencyImportBatchResponse(UUID id, long version, ImportStatus status, ImportOrigin origin,
		ImportFormat format, String fileName, int totalRows, String checksum, Counts counts,
		@Schema(nullable = true) LocalDate earliestDay, @Schema(nullable = true) LocalDate latestDay,
		int appliedRows, @Schema(nullable = true) String failureCode,
		@Schema(nullable = true) String failureMessage, Instant createdAt,
		@Schema(nullable = true) Instant confirmedAt) {

	/** Conteos de filas por clasificacion; {@code skipped} son las omitidas con motivo. */
	public record Counts(int rows, int windows, int newRows, int identical, int conflict, int invalid,
			int pendingLink, int outOfScope, int review, int skipped) {

		static Counts from(RowCounts counts) {
			return new Counts(counts.total(), counts.windows(), counts.newRows(), counts.identical(),
					counts.conflict(), counts.invalid(), counts.pendingLink(), counts.outOfScope(),
					counts.review(), counts.skipped());
		}
	}

	public static IntermittencyImportBatchResponse from(IntermittencyImportBatch batch) {
		return new IntermittencyImportBatchResponse(batch.getId(), batch.getVersion(), batch.getStatus(),
				batch.getOrigin(), batch.getFormat(), batch.getFileName(), batch.getCounts().total(), batch.getChecksum(), Counts.from(batch.getCounts()),
				batch.getEarliestDay(), batch.getLatestDay(), batch.getAppliedRows(), batch.getFailureCode(),
				batch.getFailureMessage(), batch.getCreatedAt(), batch.getConfirmedAt());
	}
}
