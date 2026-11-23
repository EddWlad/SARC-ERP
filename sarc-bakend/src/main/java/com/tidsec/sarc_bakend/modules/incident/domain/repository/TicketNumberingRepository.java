package com.tidsec.sarc_bakend.modules.incident.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketNumbering;

/**
 * Puerto del contador de numeracion de tickets (research R2): una sola fila, sin hueco ni duplicado aun con
 * creaciones simultaneas. Los metodos que escriben se ejecutan dentro de la transaccion del llamador.
 */
public interface TicketNumberingRepository {

	/** La configuracion actual; sin configurar, todos sus valores son nulos. */
	TicketNumbering current();

	/** Si ya existe algun ticket: desde entonces el numero inicial no puede cambiarse. */
	boolean ticketsExist();

	/**
	 * Toma el siguiente numero de forma atomica ({@code UPDATE … RETURNING}) y lo incrementa en la misma
	 * transaccion que crea el ticket. Vacio si la numeracion no esta configurada. Un numero asignado no se reasigna.
	 */
	Optional<Integer> nextNumber();

	/**
	 * Configura el numero inicial y el ambiente si todavia no hay tickets. Bloquea antes la fila del contador, de
	 * modo que una creacion en curso termina primero y una posterior espera. Devuelve {@code false} si ya hay tickets.
	 */
	boolean configure(int startNumber, String environment, UUID configuredBy, Instant configuredAt);
}
