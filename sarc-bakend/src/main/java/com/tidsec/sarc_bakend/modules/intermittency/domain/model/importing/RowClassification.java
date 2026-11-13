package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.util.Collection;

/**
 * Clasificacion de una fila o de una ventana interpretada (FR-025). El orden de declaracion es el de gravedad:
 * la clasificacion de una fila es la peor de las suyas (INVALID, PENDING_LINK, OUT_OF_SCOPE, CONFLICT, REVIEW,
 * IDENTICAL y NEW, de mayor a menor).
 */
public enum RowClassification {
	NEW,
	/** Ya existe una ventana identica guardada («Duplicada (ya registrada)»). */
	IDENTICAL,
	REVIEW,
	CONFLICT,
	OUT_OF_SCOPE,
	PENDING_LINK,
	INVALID;

	public static RowClassification worst(Collection<RowClassification> values) {
		RowClassification worst = NEW;
		for (RowClassification value : values) {
			if (value.ordinal() > worst.ordinal()) {
				worst = value;
			}
		}
		return worst;
	}
}
