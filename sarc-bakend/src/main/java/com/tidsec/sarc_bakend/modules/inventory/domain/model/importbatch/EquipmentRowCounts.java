package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Resumen de la vista previa: cuantas filas hay de cada clasificacion. */
public record EquipmentRowCounts(int total, int newRows, int identical, int modified, int invalid, int duplicate,
		int deletedExists, int conflict) {

	public static EquipmentRowCounts of(Collection<EquipmentRowClassification> classifications) {
		Map<EquipmentRowClassification, Integer> counts = new EnumMap<>(EquipmentRowClassification.class);
		classifications.forEach(classification -> counts.merge(classification, 1, Integer::sum));
		return new EquipmentRowCounts(classifications.size(),
				counts.getOrDefault(EquipmentRowClassification.NEW, 0),
				counts.getOrDefault(EquipmentRowClassification.IDENTICAL, 0),
				counts.getOrDefault(EquipmentRowClassification.MODIFIED, 0),
				counts.getOrDefault(EquipmentRowClassification.INVALID, 0),
				counts.getOrDefault(EquipmentRowClassification.DUPLICATE, 0),
				counts.getOrDefault(EquipmentRowClassification.DELETED_EXISTS, 0),
				counts.getOrDefault(EquipmentRowClassification.CONFLICT, 0));
	}
}
