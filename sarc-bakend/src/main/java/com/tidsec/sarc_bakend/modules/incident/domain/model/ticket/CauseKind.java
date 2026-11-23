package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/**
 * Causa de un cierre (FR-047): su descripcion es obligatoria salvo si no esta determinada, y una causa probable exige al
 * menos una evidencia del episodio que la respalde.
 */
public enum CauseKind {

	CONFIRMED,
	PROBABLE,
	UNDETERMINED;

	public boolean needsDescription() {
		return this != UNDETERMINED;
	}

	public boolean needsEvidence() {
		return this == PROBABLE;
	}
}
