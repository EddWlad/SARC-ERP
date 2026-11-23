package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/visits/{visitId}/stage}. Cancelar o aplazar exige quien decidio, la nota de
 * evidencia y la condicion para volver a escalar (FR-035); el proveedor puede indicarse al asignar la visita.
 */
public record VisitStageRequest(
		@NotNull Long version,
		@NotNull VisitStage stage,
		@Schema(nullable = true) Instant occurredAt,
		@Schema(nullable = true) @Size(max = 500) String note,
		@Schema(nullable = true) @Size(max = 120) String providerName,
		@Schema(nullable = true) @Size(max = 150) String cancelDecidedBy,
		@Schema(nullable = true) @Size(max = 500) String cancelEvidenceNote,
		@Schema(nullable = true) @Size(max = 300) String reescalationCondition) {
}
