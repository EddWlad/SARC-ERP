package com.tidsec.sarc_bakend.modules.incident.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /tickets/{id}/events/{eventId}/annul}: el motivo es obligatorio y el evento no se borra. */
public record AnnulRequest(
		@NotNull Long version,
		@NotBlank @Size(max = 300) String reason) {
}
