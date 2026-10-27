package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

/**
 * Calidad del dato (PRD 3.3). Solo {@link #VALIDATED} suma en los indicadores oficiales; las demas
 * se conservan y se informan aparte.
 */
public enum SessionState {

	VALIDATED,
	/** El equipo no se pudo identificar con seguridad; espera conciliacion (PENDIENTE_VINCULACION). */
	PENDING_LINK,
	/**
	 * Equipo identificado, pero la sesion llego por una fuente que no es la preferente del equipo
	 * (PRD 7.2): no suma hasta que un analista la apruebe, para no contar dos veces la misma carga.
	 */
	AWAITING_APPROVAL,
	/** Transaccion de prueba marcada por un analista. */
	TEST
}
