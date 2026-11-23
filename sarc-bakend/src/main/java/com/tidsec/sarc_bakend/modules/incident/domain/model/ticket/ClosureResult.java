package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/** Resultado de cierre de un episodio (FR-047). Los dos ultimos quedan fuera de los indicadores (FR-050). */
public enum ClosureResult {

	RECOVERED,
	NO_FAULT_CONFIRMED,
	/** Exige indicar el ticket original. El ticket no se reabre (FR-049). */
	DUPLICATE,
	/** El ticket no se reabre (FR-049); se crea otro si hace falta. */
	REGISTERED_IN_ERROR;

	/** Un ticket cerrado con estos resultados no se reabre y se informa aparte de los indicadores (FR-049, FR-050). */
	public boolean excludedFromIndicators() {
		return this == DUPLICATE || this == REGISTERED_IN_ERROR;
	}
}
