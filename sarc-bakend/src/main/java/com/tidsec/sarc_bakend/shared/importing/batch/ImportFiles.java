package com.tidsec.sarc_bakend.shared.importing.batch;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;

/** Reglas comunes de los originales que se cargan para importar (TRD 09.1). */
public final class ImportFiles {

	private ImportFiles() {
	}

	/** Rechaza un original vacio o mayor que el limite de lectura. */
	public static void requireContent(int length) {
		if (length == 0) {
			throw new BusinessRuleException("IMPORT_FILE_EMPTY", "No hay datos para importar");
		}
		if (length > TabularReader.MAX_BYTES) {
			throw new BusinessRuleException("IMPORT_FILE_TOO_LARGE", "El archivo supera 20 MiB");
		}
	}

	/** Nombre del archivo apto para guardarlo y mostrarlo: sin rutas ni caracteres de control. */
	public static String safeName(String name, String fallback) {
		String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
		if (clean.isEmpty()) {
			return fallback;
		}
		return clean.length() > 255 ? clean.substring(clean.length() - 255) : clean;
	}
}
