package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.util.List;

/**
 * Lo que dibuja el informe «Control de tickets» (FR-070 a FR-077), ya compuesto: el periodo exacto o «Todo el
 * historial», los filtros activos, el corte y una linea por ticket con las tres columnas del informe. La huella de las
 * filas va en el archivo para poder comprobarlo despues (FR-074a).
 */
public record ControlReportView(String period, String filters, Instant cutOff, List<Line> lines, long open, long closed,
		String rowsSha256) {

	/** Una fila: numero, elemento afectado (FR-075) y estado al final del periodo, ya en texto. */
	public record Line(String code, String element, String state) {
	}

	public int total() {
		return lines.size();
	}

	/** El pie del informe (FR-076). */
	public String totals() {
		return "Total: " + total() + " · Abiertos: " + open + " · Cerrados: " + closed;
	}
}
