package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Tipo de relacion entre tickets (FR-017). No fusiona hitos ni conteos. */
public enum RelationKind {

	/** Falla nueva de un equipo con tickets anteriores. */
	INDEPENDENT_FAILURE,
	/** La misma falla (enlace, energia del sitio) en otro equipo: un ticket por equipo, relacionados entre si. */
	COMMON_FAILURE
}
