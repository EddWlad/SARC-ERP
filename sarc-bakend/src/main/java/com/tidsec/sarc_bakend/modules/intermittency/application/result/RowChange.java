package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportBatch;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IntermittencyImportRow;

/** Una fila despues de un cambio y el lote con sus conteos al dia, para que la vista previa se actualice. */
public record RowChange(IntermittencyImportRow row, IntermittencyImportBatch batch) {
}
