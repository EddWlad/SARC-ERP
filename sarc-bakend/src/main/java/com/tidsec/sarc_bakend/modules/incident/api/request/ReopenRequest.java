package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/reopen} (FR-049): el motivo y la hora de la nueva deteccion (T0 del episodio
 * nuevo), que si es anterior al registro exige su propio motivo.
 */
public record ReopenRequest(
		@NotNull Long version,
		@NotBlank @Size(max = 500) String reason,
		@Schema(nullable = true) Instant t0,
		@Schema(nullable = true) @Size(max = 300) String t0Reason) {
}
