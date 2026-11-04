package com.tidsec.sarc_bakend.modules.uptime.domain.model.importing;

/** Que paso con la fila al confirmar el lote. */
public enum UptimeRowOutcome {

	APPLIED,
	SKIPPED,
	FAILED,
	PENDING_LINK,
	LINKED
}
