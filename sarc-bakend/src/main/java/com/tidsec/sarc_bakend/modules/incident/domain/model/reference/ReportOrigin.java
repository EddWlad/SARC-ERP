package com.tidsec.sarc_bakend.modules.incident.domain.model.reference;

/**
 * Origen del aviso (FR-004), separado del elemento afectado: que el aviso llegue por chatbot no convierte la
 * incidencia en una falla del chatbot.
 */
public enum ReportOrigin {

	PLATFORM,
	CHATBOT,
	SITE_STAFF,
	CUSTOMER,
	PROVIDER,
	/** Exige un detalle. */
	OTHER;

	/** Origen propuesto al crear un ticket de equipo (FR-010). */
	public static ReportOrigin proposed() {
		return PLATFORM;
	}
}
