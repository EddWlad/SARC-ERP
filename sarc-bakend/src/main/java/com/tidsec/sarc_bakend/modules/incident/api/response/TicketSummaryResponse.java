package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.application.result.TicketSummaryView;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una fila del listado de tickets (contracts/incidents-api.md, TicketSummary). */
public record TicketSummaryResponse(UUID id, String code, TicketState state, ElementClass elementClass,
		String elementLabel, @Schema(nullable = true) String network, IncidentType incidentType,
		String incidentTypeLabel, Priority priority, TicketResponsible responsible, Instant t0, Instant lastActivityAt,
		@Schema(nullable = true) TicketCondition condition, int episodeCount, long version,
		@Schema(nullable = true) TicketState stateAtPeriodEnd) {

	public record TicketResponsible(UUID id, String name) {
	}

	public static TicketSummaryResponse from(TicketSummaryView view) {
		return new TicketSummaryResponse(view.id(), view.code(), view.state(), view.elementClass(), view.elementLabel(),
				view.network(), view.incidentType(), view.incidentTypeLabel(), view.priority(),
				new TicketResponsible(view.responsible().id(), view.responsible().name()), view.t0(), view.lastActivityAt(),
				view.condition(), view.episodeCount(), view.version(), view.stateAtPeriodEnd());
	}
}
