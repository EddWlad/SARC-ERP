package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

import java.util.List;

/** Conteos de la vista previa. Observada no es una clasificacion: una fila nueva puede serlo. */
public record UptimeRowCounts(int total, int newRows, int identical, int conflict, int invalid, int pendingLink,
		int observed) {

	public static UptimeRowCounts of(List<UptimeImportRow> rows) {
		return new UptimeRowCounts(rows.size(), count(rows, UptimeRowClassification.NEW),
				count(rows, UptimeRowClassification.IDENTICAL), count(rows, UptimeRowClassification.CONFLICT),
				count(rows, UptimeRowClassification.INVALID), count(rows, UptimeRowClassification.PENDING_LINK),
				(int) rows.stream().filter(row -> !row.getObservations().isEmpty()).count());
	}

	private static int count(List<UptimeImportRow> rows, UptimeRowClassification classification) {
		return (int) rows.stream().filter(row -> row.getClassification() == classification).count();
	}
}
