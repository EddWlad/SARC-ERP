package com.tidsec.sarc_bakend.shared.catalog;

import java.util.UUID;

/** Conector de un equipo tal como lo necesita Incidencias: su identidad estable, su posicion y si sigue activo. */
public record IncidentConnector(UUID id, int position, String typeLabel, boolean active) {
}
