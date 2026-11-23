package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationChannel;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CommunicationKind;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/communications}: el envio real de un aviso publicado a mano (FR-058). {@code
 * channelDetail} es obligatorio si el canal es «Otro»; {@code justification}, si la hora es anterior al registro.
 */
public record CommunicationRequest(
		@NotNull Long version,
		@NotNull CommunicationChannel channel,
		@Schema(nullable = true) @Size(max = 150) String channelDetail,
		@NotNull CommunicationKind kind,
		@NotNull Instant sentAt,
		@Schema(nullable = true) @Size(max = 300) String justification) {
}
