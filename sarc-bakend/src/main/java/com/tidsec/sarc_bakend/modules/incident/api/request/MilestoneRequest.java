package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /tickets/{id}/episodes/{ordinal}/milestones/{kind}}: registra o corrige un hito. Sin
 * {@code occurredAt} es el momento actual (una sola accion). Si el hito ya tenia valor es una correccion y exige
 * {@code justification}.
 */
public record MilestoneRequest(
		@NotNull Long version,
		@NotNull MilestoneStatus status,
		@Schema(nullable = true) Instant occurredAt,
		@Schema(nullable = true) @Size(max = 300) String reason,
		@Schema(nullable = true) @Size(max = 500) String justification,
		@Schema(nullable = true) Boolean orderWarningAck) {
}
