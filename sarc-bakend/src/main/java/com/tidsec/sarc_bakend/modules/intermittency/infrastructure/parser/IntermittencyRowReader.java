package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyRowParser;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow.RawCell;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;

/**
 * Lee el registro operativo con {@link TabularReader} (solo la primera hoja del XLSX, CSV con {@code ,} o
 * {@code ;}, y texto pegado). Los encabezados se reconocen por su nombre, sin tildes ni mayusculas y con
 * espacios simples, en cualquier orden. Sin fila de encabezados rige el orden de la plantilla: Fecha, Codigo,
 * Nombre, Comentario, los pares Inicio y Fin y, al final, Total inactivo (contracts/import-format.md).
 */
@Component
class IntermittencyRowReader implements IntermittencyRowParser {

	private static final Pattern START = Pattern.compile("^inicio ?(\\d+)$");
	private static final Pattern END = Pattern.compile("^fin ?(\\d+)$");
	private static final int FIXED_COLUMNS = 4;

	private final TabularReader reader;

	IntermittencyRowReader(TabularReader reader) {
		this.reader = reader;
	}

	@Override
	public List<RawRow> parse(byte[] content) {
		return rows(reader.read(content));
	}

	@Override
	public List<RawRow> parseText(String text) {
		return rows(reader.readText(text));
	}

	private static List<RawRow> rows(List<TabularRow> tabular) {
		if (tabular.isEmpty()) {
			throw new BusinessRuleException("IMPORT_NO_ROWS", "El archivo no tiene filas de intermitencias");
		}
		TabularRow first = tabular.getFirst();
		// El orden de las columnas es libre: hay encabezados si alguna celda de la primera fila dice «Fecha».
		boolean header = first.cells().stream().anyMatch(cell -> normalize(cell).equals("fecha"));
		Map<Integer, Column> columns = header ? fromHeader(first) : fromTemplate(tabular);
		List<TabularRow> data = header ? tabular.subList(1, tabular.size()) : tabular;
		List<RawRow> rows = new ArrayList<>();
		for (TabularRow row : data) {
			List<RawCell> cells = new ArrayList<>();
			columns.forEach((index, column) -> cells.add(new RawCell(column.key(), column.label(), row.cell(index))));
			rows.add(new RawRow(row.number(), cells));
		}
		if (rows.isEmpty()) {
			throw new BusinessRuleException("IMPORT_NO_ROWS", "El archivo no tiene filas de intermitencias");
		}
		return rows;
	}

	private record Column(String key, String label) {
	}

	/** Columnas por posicion segun los nombres de la primera fila. */
	private static Map<Integer, Column> fromHeader(TabularRow header) {
		Map<Integer, Column> columns = new LinkedHashMap<>();
		Map<String, Integer> starts = new LinkedHashMap<>();
		Map<String, Integer> ends = new LinkedHashMap<>();
		for (int index = 0; index < header.cells().size(); index++) {
			String label = header.cell(index);
			String name = normalize(label);
			Matcher start = START.matcher(name);
			Matcher end = END.matcher(name);
			String key = null;
			if (name.equals("fecha")) {
				key = RawRow.DATE;
			}
			else if (name.equals("codigo cargador") || name.equals("codigo")) {
				key = RawRow.CODE;
			}
			else if (name.equals("nombre cargador") || name.equals("nombre")) {
				key = RawRow.NAME;
			}
			else if (name.equals("comentario")) {
				key = RawRow.COMMENT;
			}
			else if (name.equals("total inactivo") || name.equals("total")) {
				key = RawRow.TOTAL;
			}
			else if (start.matches()) {
				key = RawRow.start(Integer.parseInt(start.group(1)));
				starts.put(key, index);
			}
			else if (end.matches()) {
				key = RawRow.end(Integer.parseInt(end.group(1)));
				ends.put(key, index);
			}
			String resolved = key;
			if (resolved != null && columns.values().stream().noneMatch(column -> column.key().equals(resolved))) {
				columns.put(index, new Column(resolved, label));
			}
		}
		requireColumn(columns, RawRow.DATE, "Fecha");
		requireColumn(columns, RawRow.CODE, "Código cargador");
		for (Map.Entry<String, Integer> start : starts.entrySet()) {
			String pair = start.getKey().substring(5);
			if (!ends.containsKey(RawRow.end(Integer.parseInt(pair)))) {
				throw missing("Fin " + pair);
			}
		}
		for (String end : ends.keySet()) {
			String pair = end.substring(3);
			if (!starts.containsKey(RawRow.start(Integer.parseInt(pair)))) {
				throw missing("Inicio " + pair);
			}
		}
		if (starts.isEmpty()) {
			throw missing("Inicio 1");
		}
		return columns;
	}

	/**
	 * Orden de la plantilla. Con {@code k} columnas despues de la cuarta: si {@code k} es impar la ultima es el
	 * Total inactivo y las anteriores forman pares; si es par, todas forman pares y no hay total.
	 */
	private static Map<Integer, Column> fromTemplate(List<TabularRow> rows) {
		int width = rows.stream().mapToInt(row -> lastFilled(row) + 1).max().orElse(0);
		if (width < 2) {
			throw missing(width == 0 ? "Fecha" : "Código cargador");
		}
		int k = Math.max(0, width - FIXED_COLUMNS);
		if (k == 0) {
			throw missing("Inicio 1");
		}
		Map<Integer, Column> columns = new LinkedHashMap<>();
		columns.put(0, new Column(RawRow.DATE, "Fecha"));
		columns.put(1, new Column(RawRow.CODE, "Código cargador"));
		columns.put(2, new Column(RawRow.NAME, "Nombre cargador"));
		columns.put(3, new Column(RawRow.COMMENT, "Comentario"));
		int pairColumns = k % 2 == 1 ? k - 1 : k;
		for (int offset = 0; offset < pairColumns; offset++) {
			int pair = offset / 2 + 1;
			boolean start = offset % 2 == 0;
			columns.put(FIXED_COLUMNS + offset, start ? new Column(RawRow.start(pair), "Inicio " + pair)
					: new Column(RawRow.end(pair), "Fin " + pair));
		}
		if (k % 2 == 1) {
			columns.put(FIXED_COLUMNS + pairColumns, new Column(RawRow.TOTAL, "Total inactivo"));
		}
		return columns;
	}

	private static int lastFilled(TabularRow row) {
		for (int index = row.cells().size() - 1; index >= 0; index--) {
			if (!row.cell(index).isEmpty()) {
				return index;
			}
		}
		return -1;
	}

	private static void requireColumn(Map<Integer, Column> columns, String key, String label) {
		if (columns.values().stream().noneMatch(column -> column.key().equals(key))) {
			throw missing(label);
		}
	}

	private static BusinessRuleException missing(String column) {
		return new BusinessRuleException("IMPORT_HEADER_MISSING",
				"Falta la columna «" + column + "». Revise los encabezados del archivo.");
	}

	/** Sin tildes, en minusculas y con espacios simples. */
	static String normalize(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
	}
}
