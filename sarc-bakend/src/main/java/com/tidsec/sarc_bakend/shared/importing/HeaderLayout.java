package com.tidsec.sarc_bakend.shared.importing;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/**
 * Ubica la fila de encabezados de un reporte original y la posicion de cada columna requerida, sin
 * depender del orden ni de columnas extra (PRD RF-003.1: se importan los reportes tal como salen de
 * la plataforma). La comparacion ignora mayusculas, tildes y espacios repetidos; un encabezado
 * tambien coincide si agrega una aclaracion entre parentesis, p. ej. "INICIO (UTC-05:00)".
 */
public final class HeaderLayout {

	/** Columna requerida: clave interna y nombres con que la escribe la fuente. */
	public record Column(String key, List<String> names) {

		public Column(String key, String... names) {
			this(key, List.of(names));
		}
	}

	/** Filas iniciales donde se busca el encabezado (titulos, fechas de generacion, logos). */
	private static final int SCAN_ROWS = 10;

	private final TabularRow headerRow;
	private final Map<String, Integer> indexes;
	private final List<TabularRow> dataRows;

	private HeaderLayout(TabularRow headerRow, Map<String, Integer> indexes, List<TabularRow> dataRows) {
		this.headerRow = headerRow;
		this.indexes = indexes;
		this.dataRows = dataRows;
	}

	public static HeaderLayout locate(List<TabularRow> rows, List<Column> columns) {
		List<String> bestMissing = columns.stream().map(column -> column.names().getFirst()).toList();
		for (int position = 0; position < Math.min(SCAN_ROWS, rows.size()); position++) {
			TabularRow candidate = rows.get(position);
			Map<String, Integer> indexes = new HashMap<>();
			for (Column column : columns) {
				int index = indexOf(candidate, column);
				if (index >= 0) {
					indexes.put(column.key(), index);
				}
			}
			if (indexes.size() == columns.size()) {
				return new HeaderLayout(candidate, Map.copyOf(indexes), List.copyOf(rows.subList(position + 1, rows.size())));
			}
			List<String> missing = columns.stream().filter(column -> !indexes.containsKey(column.key()))
					.map(column -> column.names().getFirst()).toList();
			if (missing.size() < bestMissing.size()) {
				bestMissing = missing;
			}
		}
		throw new BusinessRuleException("IMPORT_COLUMNS_MISSING",
				"El archivo no corresponde a la fuente elegida; faltan las columnas: " + String.join(", ", bestMissing));
	}

	public List<TabularRow> dataRows() {
		return dataRows;
	}

	public String value(TabularRow row, String key) {
		return row.cell(indexes.get(key));
	}

	/** Encabezado tal como esta en el archivo (sirve para leer aclaraciones como la zona horaria). */
	public String header(String key) {
		return headerRow.cell(indexes.get(key));
	}

	public static String normalize(String text) {
		String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	private static int indexOf(TabularRow row, Column column) {
		for (int index = 0; index < row.cells().size(); index++) {
			String header = normalize(row.cell(index));
			for (String name : column.names()) {
				String expected = normalize(name);
				if (header.equals(expected) || header.startsWith(expected + " (")) {
					return index;
				}
			}
		}
		return -1;
	}
}
