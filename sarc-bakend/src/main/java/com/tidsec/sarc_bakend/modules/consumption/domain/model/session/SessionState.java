package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

/**
 * Calidad del dato (PRD 3.3). Solo {@link #VALIDATED} suma en los indicadores oficiales; las demas
 * se conservan y se informan aparte.
 */
public enum SessionState {

	VALIDATED,
	/** El equipo no se pudo identificar con seguridad; espera conciliacion (PENDIENTE_VINCULACION). */
	PENDING_LINK,
	/** Transaccion de prueba marcada por un analista. */
	TEST
}
