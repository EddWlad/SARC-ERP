package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

/** Decision explicita sobre una ventana interpretada (FR-027 a FR-029). */
public enum WindowResolution {
	/** Se conserva la ventana ya registrada y no se crea la del archivo. */
	KEEP_EXISTING,
	/** La ventana ya registrada se reemplaza con una revision justificada. */
	REPLACE,
	/** Dos ventanas solapadas de la misma fila quedan en una sola que abarca a ambas. */
	MERGE,
	/** La ventana se ignora: no se crea ni cuenta en el total recalculado. */
	OMIT,
	/** Un par con solo el inicio se registra como ventana abierta. */
	OPEN
}
