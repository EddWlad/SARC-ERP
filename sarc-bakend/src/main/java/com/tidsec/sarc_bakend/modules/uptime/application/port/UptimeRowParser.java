package com.tidsec.sarc_bakend.modules.uptime.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularSheet;

/** Interpreta las filas de un reporte de uptime, sin decidir a que conector pertenecen. */
public interface UptimeRowParser {

	/**
	 * Una fila con {@code issues} no se pudo interpretar: sus valores no son confiables. {@code sheet} y
	 * {@code sheetRow} dicen donde estaba en el archivo; {@code rowNumber} la ordena en el lote.
	 */
	record ParsedRow(int rowNumber, String sheet, Integer sheetRow, String site, String charger, String connector,
			Instant periodStart, Instant periodEnd, MeasurementRules.Values values, Map<String, Object> sourceValues,
			List<RowIssue> issues) {

		public boolean isValid() {
			return issues.isEmpty();
		}
	}

	/** Contexto elegido al cargar; nunca sustituye los limites de la fuente. */
	record DeclaredPeriod(LocalDate referenceWeek) {
	}

	/** {@code period} es nulo cuando cada fila trae su periodo. */
	List<ParsedRow> parse(List<TabularSheet> sheets, DeclaredPeriod period);
    /** Inspeccion de procedencia: permite fuente sin limites y sin contexto supuesto. */
    List<ParsedRow> inspectSource(List<TabularSheet> sheets);
}
