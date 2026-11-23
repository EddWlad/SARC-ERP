package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.io.Serializable;
import java.util.UUID;

/** Clave de una relacion: el par ordenado de tickets (el menor primero). */
public record TicketRelationId(UUID ticketId, UUID relatedTicketId) implements Serializable {
}
