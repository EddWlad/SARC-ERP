package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

import java.util.UUID;

/** Lote, fila y par del archivo de donde sale una ventana importada (FR-030). */
public record ImportProvenance(UUID batchId, int rowNumber, int pairNumber) {

	public ImportProvenance {
		if (batchId == null || rowNumber < 1 || pairNumber < 1) {
			throw new IllegalArgumentException("La procedencia necesita lote, fila y par validos");
		}
	}
}
