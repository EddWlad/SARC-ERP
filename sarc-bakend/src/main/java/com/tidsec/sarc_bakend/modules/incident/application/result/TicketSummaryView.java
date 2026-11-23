package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Una fila del listado de tickets (FR-061). {@code elementLabel} usa el nombre actual del equipo (D12, FR-075);
 * {@code network} es la red conservada en T0, nula para el chatbot y los elementos del sitio. {@code t0} es el del
 * episodio vigente y {@code condition} se deriva de los hitos (FR-062). {@code stateAtPeriodEnd} solo va cuando se
 * consulto un periodo (FR-074).
 */
public record TicketSummaryView(UUID id, String code, TicketState state, ElementClass elementClass,
		String elementLabel, String network, IncidentType incidentType, String incidentTypeLabel, Priority priority,
		ResponsibleView responsible, Instant t0, Instant lastActivityAt, TicketCondition condition, int episodeCount,
		long version, TicketState stateAtPeriodEnd) {
}
