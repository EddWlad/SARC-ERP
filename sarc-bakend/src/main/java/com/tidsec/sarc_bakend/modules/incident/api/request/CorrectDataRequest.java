package com.tidsec.sarc_bakend.modules.incident.api.request;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /tickets/{id}}: corrige la descripcion o el origen del aviso con justificacion (FR-036). El
 * elemento afectado no se corrige: se cierra como registrado por error y se crea otro ticket.
 */
public record CorrectDataRequest(
		@NotNull Long version,
		@Schema(nullable = true) @Size(max = 500) String description,
		@Schema(nullable = true) ReportOrigin reportOrigin,
		@Schema(nullable = true) @Size(max = 200) String reportOriginDetail,
		@NotBlank @Size(max = 500) String justification) {
}
