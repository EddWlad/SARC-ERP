package com.tidsec.sarc_bakend.shared.importing.batch;

import java.util.Objects;

import com.tidsec.sarc_bakend.shared.error.SarcException;

/**
 * Motivo por el que fallo un trabajo de importacion, tal como se guarda en el lote: el codigo y el
 * mensaje de negocio, o uno generico si fue un error inesperado (que no se expone al usuario).
 */
public record ImportFailure(String code, String message, boolean unexpected) {

	public static ImportFailure of(RuntimeException ex) {
		if (ex instanceof SarcException sarc) {
			return new ImportFailure(sarc.code(), Objects.requireNonNullElse(sarc.getMessage(), sarc.code()), false);
		}
		return new ImportFailure("IMPORT_FAILED",
				"Error inesperado al procesar el lote; reintente y, si persiste, contacte a soporte", true);
	}
}
