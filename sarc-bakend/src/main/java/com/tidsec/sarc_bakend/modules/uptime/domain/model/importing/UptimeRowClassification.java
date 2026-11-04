package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

/** Lo que significa una fila frente a lo ya registrado. */
public enum UptimeRowClassification {

	NEW,
	IDENTICAL,
	CONFLICT,
	INVALID,
	PENDING_LINK
}
