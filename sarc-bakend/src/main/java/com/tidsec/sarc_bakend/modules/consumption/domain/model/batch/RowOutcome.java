package com.tidsec.sarc_bakend.modules.consumption.domain.model.batch;

/** Que paso con una fila al confirmar el lote. */
public enum RowOutcome {

	INSERTED,
	CORRECTED,
	/** La sesion ya tenia esos datos (otra importacion la trajo antes): no hubo cambio. */
	UNCHANGED,
	/** La sesion cambio despues de la vista previa y la correccion no estaba aprobada. */
	SKIPPED
}
