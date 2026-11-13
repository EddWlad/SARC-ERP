package com.tidsec.sarc_bakend.modules.intermittency.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;

/**
 * Lee el registro operativo y lo entrega como filas crudas por columna, sin interpretar los valores (eso lo hace
 * el {@code ImportRowInterpreter}). Reconoce los encabezados por su nombre, en cualquier orden, y si no hay fila
 * de encabezados usa el orden de la plantilla operativa (contracts/import-format.md).
 */
public interface IntermittencyRowParser {

	/** XLSX (solo la primera hoja, D09) o CSV con coma o punto y coma. */
	List<RawRow> parse(byte[] content);

	/** Bloque pegado desde una hoja de calculo: columnas separadas por tabulador. */
	List<RawRow> parseText(String text);
}
