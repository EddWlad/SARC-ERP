package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;

/**
 * Todo lo que {@link Ticket#open(NewTicket)} necesita para abrir un ticket: el numero ya asignado por el contador, el
 * elemento afectado en T0, el responsable inicial (el autor) y la version de referencias vigente en T0. {@code t0} es
 * el instante informado, y {@code t0Reason} la justificacion si difiere de {@code now} en mas de un minuto.
 */
public record NewTicket(int number, UUID clientRequestId, AffectedElement element, IncidentType type,
		ReportOrigin origin, String originDetail, String description, Priority priority, UUID responsibleId,
		String responsibleName, String duplicateKey, String duplicateOverrideReason, Instant t0, String t0Reason,
		UUID referenceVersionId, UUID actorId, Instant now) {
}
