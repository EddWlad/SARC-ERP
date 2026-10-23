package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Resumen de la vista previa: cuantas filas hay de cada clasificacion. */
public record RowCounts(int total, int newRows, int identical, int modified, int invalid, int duplicate,
		int pendingLink) {

	public static RowCounts of(Collection<RowClassification> classifications) {
		Map<RowClassification, Integer> counts = new EnumMap<>(RowClassification.class);
		classifications.forEach(classification -> counts.merge(classification, 1, Integer::sum));
		return new RowCounts(classifications.size(), counts.getOrDefault(RowClassification.NEW, 0),
				counts.getOrDefault(RowClassification.IDENTICAL, 0), counts.getOrDefault(RowClassification.MODIFIED, 0),
				counts.getOrDefault(RowClassification.INVALID, 0), counts.getOrDefault(RowClassification.DUPLICATE, 0),
				counts.getOrDefault(RowClassification.PENDING_LINK, 0));
	}
}
