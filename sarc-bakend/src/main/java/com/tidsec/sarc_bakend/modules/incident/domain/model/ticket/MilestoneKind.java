package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Hitos de un episodio (FR-040). */
public enum MilestoneKind {

	/** Momento en que la plataforma, el chatbot, el personal o el cliente informa la novedad. */
	T0,
	/** Primera validacion confirmada. */
	T1,
	/** Primera solicitud de apoyo o primera accion correctiva. */
	T2,
	/** Recuperacion de la comunicacion o de la funcionalidad. */
	T3,
	/** Cierre, despues de comprobar la estabilidad y completar el registro. */
	T4;

	/** T1 a T3: los hitos que admiten «No aplica» y «Sin dato». */
	public boolean admitsNoTime() {
		return this == T1 || this == T2 || this == T3;
	}
}
