package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

/**
 * Los unicos estados principales de un ticket (FR-030). Ningun hito ni evento agrega uno: validacion, atencion y
 * recuperacion se derivan de los hitos. Un ticket esta {@code OPEN} si y solo si su ultimo episodio esta abierto.
 */
public enum TicketState {

	OPEN,
	CLOSED
}
