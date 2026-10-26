package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportBatch;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowCounts;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportOrigin;
import com.tidsec.sarc_bakend.shared.importing.batch.ImportStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lote de carga masiva de equipos: etapa, conteos por clasificacion y resultado. */
public record EquipmentImportBatchResponse(
		UUID id,
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
		int deletedExistsRows,
		int conflictRows,
		int appliedRows,
		Instant createdAt,
		@Schema(nullable = true) Instant confirmedAt,
		long version) {

	public static EquipmentImportBatchResponse from(EquipmentImportBatch batch) {
		EquipmentRowCounts counts = batch.getCounts();
		return new EquipmentImportBatchResponse(batch.getId(), batch.getOrigin(), batch.getFileName(),
				batch.getChecksum(), batch.getStatus(), batch.getFailureCode(), batch.getFailureMessage(),
				counts.total(), counts.newRows(), counts.identical(), counts.modified(), counts.invalid(),
				counts.duplicate(), counts.deletedExists(), counts.conflict(), batch.getAppliedRows(),
				batch.getCreatedAt(), batch.getConfirmedAt(), batch.getVersion());
	}
}
