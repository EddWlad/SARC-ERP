package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.util.Collection;

/**
 * Conteos de un lote por clasificacion de fila (FR-025). Una fila omitida con motivo cuenta solo en
 * {@code skipped}; el resto, en la clasificacion que tiene hoy. {@code windows} son las ventanas activas.
 */
public record RowCounts(int total, int windows, int newRows, int identical, int conflict, int invalid,
		int pendingLink, int outOfScope, int review, int skipped) {

	public static RowCounts of(Collection<IntermittencyImportRow> rows) {
		int[] counts = new int[RowClassification.values().length];
		int skipped = 0;
		int windows = 0;
		for (IntermittencyImportRow row : rows) {
			if (row.getDecision() == RowDecision.SKIP) {
				skipped++;
				continue;
			}
			counts[row.getClassification().ordinal()]++;
			windows += row.getInterpretation().active().size();
		}
		return new RowCounts(rows.size(), windows, counts[RowClassification.NEW.ordinal()],
				counts[RowClassification.IDENTICAL.ordinal()], counts[RowClassification.CONFLICT.ordinal()],
				counts[RowClassification.INVALID.ordinal()], counts[RowClassification.PENDING_LINK.ordinal()],
				counts[RowClassification.OUT_OF_SCOPE.ordinal()], counts[RowClassification.REVIEW.ordinal()],
				skipped);
	}
}
