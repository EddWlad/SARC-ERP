package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;

/** Una fila pendiente de resolver y el lote confirmado del que viene. */
public record PendingRow(IntermittencyImportRow row, IntermittencyImportBatch batch) {
}
