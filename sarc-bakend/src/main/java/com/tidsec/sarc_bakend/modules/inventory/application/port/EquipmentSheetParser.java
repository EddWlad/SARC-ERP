package com.tidsec.sarc_bakend.modules.inventory.application.port;

import java.util.List;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;

/** Puerto: ubica las columnas de la plantilla de equipos y entrega cada fila de datos sin interpretar. */
public interface EquipmentSheetParser {

	/** Fila de datos con su numero en el archivo original (para el analista). */
	record SheetLine(int rowNumber, EquipmentLine line) {
	}

	/** Rechaza el archivo si no tiene las columnas de la plantilla. Omite las filas vacias. */
	List<SheetLine> parse(List<TabularRow> rows);
}
