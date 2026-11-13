package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

/** Que se hace con una fila al confirmar el lote. Con SKIP la razon es obligatoria. */
public enum RowDecision {
	APPLY,
	SKIP,
	/** La fila queda pendiente de resolver (punto por vincular o conflicto) y bloquea la cobertura de sus dias. */
	KEEP_PENDING
}
