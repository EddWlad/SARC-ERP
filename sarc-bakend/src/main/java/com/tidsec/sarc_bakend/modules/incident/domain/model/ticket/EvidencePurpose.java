package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Para que se carga una evidencia: general, inicial (al crear el ticket) o final (al cierre, FR-037). */
public enum EvidencePurpose {

	GENERAL,
	INITIAL,
	FINAL
}
