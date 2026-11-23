package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.UUID;

/** Un conector afectado, por identidad (D06) y con la posicion que mostraba al crear el ticket. */
public record ConnectorRef(UUID connectorId, int positionAtT0) {
}
