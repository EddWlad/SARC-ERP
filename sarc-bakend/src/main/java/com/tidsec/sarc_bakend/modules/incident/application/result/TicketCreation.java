package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.util.UUID;

/** El ticket resultante de una creacion; {@code created} es falso si el envio repetido devolvio el ya creado. */
public record TicketCreation(UUID ticketId, boolean created) {
}
