package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Momento del aviso: el inicial, una actualizacion o el cierre. El de cierre cumple el requisito de cierre (FR-047). */
public enum CommunicationKind {

	INITIAL,
	UPDATE,
	CLOSURE
}
