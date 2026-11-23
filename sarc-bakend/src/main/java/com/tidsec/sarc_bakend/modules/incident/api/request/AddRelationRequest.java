package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.RelationKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /tickets/{id}/relations}. {@code version} es la que leyo el cliente (409 si cambio). */
public record AddRelationRequest(
		@NotNull Long version,
		@NotNull UUID relatedTicketId,
		@NotNull RelationKind kind,
		@Schema(nullable = true) @Size(max = 300) String reason) {
}
