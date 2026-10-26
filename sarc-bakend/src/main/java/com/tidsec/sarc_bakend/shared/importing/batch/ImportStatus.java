package com.tidsec.sarc_bakend.shared.importing.batch;

/** Ciclo de vida de un lote: interpretar, revisar, confirmar. */
public enum ImportStatus {

	/** Interpretando el archivo y clasificando las filas (en segundo plano). */
	PARSING,
	/** Vista previa lista: el analista revisa y decide. */
	READY,
	/** Incorporando las filas aprobadas (en segundo plano). */
	CONFIRMING,
	COMPLETED,
	/** Fallo al interpretar o al confirmar; si fallo al confirmar se puede reintentar. */
	FAILED,
	/** Descartado sin incorporar nada. */
	DISCARDED
}
