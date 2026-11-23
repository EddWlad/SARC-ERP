package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/events}. Solo admite los tipos manuales (validacion, accion correctiva,
 * escalamiento y observacion); un escalamiento exige {@code targetKind}.
 */
public record EventRequest(
		@NotNull Long version,
		@NotNull EventType type,
		@Schema(nullable = true) Instant occurredAt,
		@NotBlank @Size(max = 1000) String text,
		@Schema(nullable = true) TargetKind targetKind,
		@Schema(nullable = true) @Size(max = 150) String targetRoleOrProvider) {
}
