package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

/** Clasificacion de una fila frente a lo ya importado (PRD 3.1 y RF-003.5). */
public enum RowClassification {

	/** Sesion que no existe: se incorpora. */
	NEW,
	/** Ya existe con los mismos datos: no se toca (reimportacion idempotente). */
	IDENTICAL,
	/** Ya existe con datos distintos del mismo cargador: se corrige solo si el analista lo aprueba. */
	MODIFIED,
	/**
	 * Ya existe, pero llega en otro cargador (serie, o ACTIVO y CARGADOR): no es una correccion. Solo
	 * se aplica si el analista la reasigna de forma explicita; aprobar no basta.
	 */
	EQUIPMENT_CHANGED,
	/** Sesion pendiente con los mismos datos cuyo par ya resuelve un unico equipo: se vincula al confirmar. */
	LINKABLE,
	/** No se pudo interpretar sin adivinar: no se incorpora. */
	INVALID,
	/** El mismo ID de sesion aparece antes en el mismo lote: se ignora. */
	DUPLICATE,
	/** Sesion nueva de un equipo que no se pudo identificar: se guarda pendiente de vinculacion. */
	PENDING_LINK;

	/** Clasificaciones que pueden terminar escribiendo una sesion. */
	public boolean writesSession() {
		return this == NEW || this == MODIFIED || this == EQUIPMENT_CHANGED || this == LINKABLE
				|| this == PENDING_LINK;
	}
}
