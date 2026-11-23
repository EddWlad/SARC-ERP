package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /tickets/{id}/responsible}: solo un Analista o Administrador activo. */
public record ResponsibleRequest(
		@NotNull Long version,
		@NotNull UUID responsibleId) {
}
