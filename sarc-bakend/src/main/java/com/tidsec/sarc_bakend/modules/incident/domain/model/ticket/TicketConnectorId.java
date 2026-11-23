package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.io.Serializable;
import java.util.UUID;

/** Clave de un conector afectado ({@code ticket} es el id del ticket). */
public record TicketConnectorId(UUID ticket, UUID connectorId) implements Serializable {
}
