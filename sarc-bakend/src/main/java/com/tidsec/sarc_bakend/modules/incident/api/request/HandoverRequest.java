package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/handover} (FR-033): el entrante, el estado actual, los pendientes, la proxima
 * verificacion y el criterio previsto de cierre. El saliente es el responsable vigente.
 */
public record HandoverRequest(
		@NotNull Long version,
		@NotNull UUID incomingResponsibleId,
		@NotBlank @Size(max = 500) String currentStatus,
		@NotBlank @Size(max = 500) String pending,
		@Schema(nullable = true) Instant nextCheckAt,
		@NotBlank @Size(max = 500) String closureCriterion) {
}
