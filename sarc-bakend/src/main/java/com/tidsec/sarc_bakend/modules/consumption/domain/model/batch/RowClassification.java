package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

/** Clasificacion de una fila frente a lo ya importado (PRD 3.1 y RF-003.5). */
public enum RowClassification {

	/** Sesion que no existe: se incorpora. */
	NEW,
	/** Ya existe con los mismos datos: no se toca (reimportacion idempotente). */
	IDENTICAL,
	/** Ya existe con datos distintos: se corrige solo si el analista lo aprueba. */
	MODIFIED,
	/** No se pudo interpretar sin adivinar: no se incorpora. */
	INVALID,
	/** El mismo ID de sesion aparece antes en el mismo lote: se ignora. */
	DUPLICATE,
	/** Sesion nueva de un equipo que no se pudo identificar: se guarda pendiente de vinculacion. */
	PENDING_LINK;

	/** Clasificaciones que pueden terminar escribiendo una sesion. */
	public boolean writesSession() {
		return this == NEW || this == MODIFIED || this == PENDING_LINK;
	}
}
