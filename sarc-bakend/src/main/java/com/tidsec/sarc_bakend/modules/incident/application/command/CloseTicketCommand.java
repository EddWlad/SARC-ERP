package com.tidsec.sarc_bakend.modules.incident.application.command;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CauseKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureCheck;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;

/**
 * Entrada del cierre de un ticket (FR-047). Todo es opcional: lo que falta lo informa el cierre como requisito
 * pendiente ({@code TICKET_CLOSE_REQUIREMENTS}), en vez de rechazar la solicitud entera.
 *
 * @param t4 la hora de cierre; nula es el momento actual
 * @param closureChecks las confirmaciones por clave, solo de un equipo
 */
public record CloseTicketCommand(UUID ticketId, long version, Instant t4, String t4Reason, ClosureResult result,
		UUID duplicateOfTicketId, CauseKind causeKind, String causeText, String recommendations,
		Map<String, ClosureCheck> closureChecks, String finalEvidenceWaiver, String closureCommunicationWaiver,
		String deviationJustification) {
}
