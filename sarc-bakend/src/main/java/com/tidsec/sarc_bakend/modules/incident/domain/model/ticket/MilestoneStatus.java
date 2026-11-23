package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/**
 * Estado de un hito (FR-042). El sistema nunca inventa una hora ni convierte un hito sin hora en cero: solo
 * {@code RECORDED} tiene hora.
 */
public enum MilestoneStatus {

	/** Solo en un episodio abierto. */
	PENDING,
	RECORDED,
	/** Con motivo. */
	NOT_APPLICABLE,
	/** Con motivo, cuando la hora real se desconoce. */
	NO_DATA;

	public boolean hasTime() {
		return this == RECORDED;
	}
}
