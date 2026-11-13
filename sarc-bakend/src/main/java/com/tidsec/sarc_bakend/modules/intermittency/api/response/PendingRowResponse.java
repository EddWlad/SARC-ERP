package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.PendingRow;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila pendiente de resolver con el lote, el archivo y el momento en que se confirmo (FR-028). */
public record PendingRowResponse(UUID batchId, @Schema(nullable = true) String fileName, Instant batchCreatedAt,
		IntermittencyImportRowResponse row) {

	public static PendingRowResponse from(PendingRow pending) {
		return new PendingRowResponse(pending.batch().getId(), pending.batch().getFileName(),
				pending.batch().getCreatedAt(), IntermittencyImportRowResponse.from(pending.row()));
	}
}
