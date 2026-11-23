package com.tidsec.sarc_bakend.modules.incident.api.request;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /tickets/{id}/priority}: cambiar la prioridad exige un motivo (P-05). */
public record PriorityRequest(
		@NotNull Long version,
		@NotNull Priority priority,
		@NotBlank @Size(max = 500) String reason) {
}
