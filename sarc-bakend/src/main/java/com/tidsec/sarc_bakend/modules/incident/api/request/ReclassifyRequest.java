package com.tidsec.sarc_bakend.modules.incident.api.request;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /tickets/{id}/reclassify}: el tipo debe ser compatible con la clase del elemento. */
public record ReclassifyRequest(
		@NotNull Long version,
		@NotNull IncidentType incidentType,
		@NotBlank @Size(max = 500) String reason) {
}
