package com.tidsec.sarc_bakend.shared.importing.reading;

import java.util.List;

/** Hoja de un libro con su nombre; un texto pegado o un CSV es una sola hoja sin nombre. */
public record TabularSheet(String name, List<TabularRow> rows) {

	public TabularSheet {
		rows = List.copyOf(rows);
	}
}
