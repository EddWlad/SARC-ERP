package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.util.List;

/**
 * Valores originales de una fila del archivo, por columna y en el orden en que estaban. {@code key} es la columna
 * canonica ({@code date}, {@code code}, {@code name}, {@code comment}, {@code startN}, {@code endN},
 * {@code total}) y {@code label} el encabezado tal como venia. No se modifica nunca (FR-026).
 */
public record RawRow(int rowNumber, List<RawCell> cells) {

	public static final String DATE = "date";
	public static final String CODE = "code";
	public static final String NAME = "name";
	public static final String COMMENT = "comment";
	public static final String TOTAL = "total";

	public RawRow {
		cells = List.copyOf(cells);
	}

	/** Una celda de la fila; {@code value} es el texto leido, sin interpretar. */
	public record RawCell(String key, String label, String value) {
	}

	public static String start(int pair) {
		return "start" + pair;
	}

	public static String end(int pair) {
		return "end" + pair;
	}

	/** Valor de la columna, o cadena vacia si la fila no la trae. */
	public String get(String key) {
		return cells.stream().filter(cell -> cell.key().equals(key)).map(RawCell::value).findFirst().orElse("");
	}

	/** Numeros de par que tienen alguna columna en la fila, en orden. */
	public List<Integer> pairs() {
		return cells.stream().map(RawCell::key).filter(key -> key.startsWith("start") || key.startsWith("end"))
				.map(key -> Integer.parseInt(key.substring(key.startsWith("start") ? 5 : 3))).distinct().sorted()
				.toList();
	}
}
