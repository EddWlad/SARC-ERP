package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.RowChange;

/** La fila despues del cambio y el lote con sus conteos al dia. */
public record RowChangeResponse(IntermittencyImportRowResponse row, IntermittencyImportBatchResponse batch) {

	public static RowChangeResponse from(RowChange change) {
		return new RowChangeResponse(IntermittencyImportRowResponse.from(change.row()),
				IntermittencyImportBatchResponse.from(change.batch()));
	}
}
