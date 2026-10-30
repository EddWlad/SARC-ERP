package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

/** Clasificacion de una fila de la carga masiva frente al inventario (RF-002, PRD 6.3). */
public enum EquipmentRowClassification {

	/** La serie no existe: se registra un equipo nuevo. */
	NEW,
	/** El equipo ya tiene esos datos: no se toca. */
	IDENTICAL,
	/** El equipo existe con datos distintos: se actualiza solo si el analista lo aprueba. */
	MODIFIED,
	/** El equipo existe y solo se completa su identidad Enerlink: se aplica al confirmar, sin escribir el equipo. */
	IDENTITY_COMPLETED,
	/** Faltan datos o no cumplen las reglas del equipo: no se incorpora. */
	INVALID,
	/** La serie, el codigo, el UID/PUK o una identidad externa aparecen antes en el mismo lote. */
	DUPLICATE,
	/** La serie es de un equipo eliminado: se recupera ese equipo, no se crea otro. */
	DELETED_EXISTS,
	/** Los identificadores pertenecen a otro equipo o a equipos distintos: no se fusionan. */
	CONFLICT;

	/** Clasificaciones que pueden escribir en el inventario al confirmar. */
	public boolean writesEquipment() {
		return this == NEW || this == MODIFIED || this == IDENTITY_COMPLETED;
	}
}
