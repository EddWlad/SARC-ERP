package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /tickets/{id}/visits}: solicita una visita tecnica; el proveedor puede conocerse despues. */
public record VisitRequest(
		@NotNull Long version,
		@Schema(nullable = true) @Size(max = 120) String providerName,
		@Schema(nullable = true) Instant occurredAt,
		@Schema(nullable = true) @Size(max = 500) String note) {
}
