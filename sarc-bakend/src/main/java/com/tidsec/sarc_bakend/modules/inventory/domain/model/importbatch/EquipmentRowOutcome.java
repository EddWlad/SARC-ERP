package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

/** Que paso con una fila al confirmar el lote. */
public enum EquipmentRowOutcome {

	CREATED,
	UPDATED,
	/** Se completo la identidad externa del equipo, sin escribirlo. */
	IDENTITY_ADDED,
	/** El equipo ya tenia esos datos al confirmar: no hubo cambio. */
	UNCHANGED,
	/** No se aplico: sin aprobar, o el inventario cambio despues de la vista previa (ver el detalle). */
	SKIPPED
}
