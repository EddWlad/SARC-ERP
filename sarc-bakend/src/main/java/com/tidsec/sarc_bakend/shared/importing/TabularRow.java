package com.tidsec.sarc_bakend.shared.importing;

import java.util.List;

/**
 * Fila leida de un archivo o de un pegado. {@code number} es la fila tal como la ve el usuario en
 * su hoja (empieza en 1), para que los errores de la vista previa se puedan ubicar en el original.
 */
public record TabularRow(int number, List<String> cells) {

	public TabularRow {
		cells = List.copyOf(cells);
	}

	/** Celda recortada; vacia si la fila es mas corta. */
	public String cell(int index) {
		return index < cells.size() ? cells.get(index).strip() : "";
	}

	public boolean isBlank() {
		return cells.stream().allMatch(String::isBlank);
	}
}
