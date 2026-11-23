package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EvidencePurpose;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Parte {@code meta} de {@code POST /tickets/{id}/evidence}: la parte {@code file} lleva el archivo. */
public record EvidenceMeta(
		@NotNull Long version,
		@NotNull EvidencePurpose purpose,
		@Schema(nullable = true) UUID eventId,
		@Schema(nullable = true) UUID communicationId,
		@Schema(nullable = true) @Size(max = 500) String note) {
}
