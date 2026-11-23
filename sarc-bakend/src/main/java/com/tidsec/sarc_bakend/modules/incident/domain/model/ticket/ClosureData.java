package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que se guarda en un episodio al cerrarlo (FR-047): la hora de cierre T4, el resultado, la causa y las
 * confirmaciones y justificaciones. Los requisitos ya se validaron con {@code ClosureRequirements}; el episodio solo
 * comprueba el orden y la hora de T4.
 *
 * @param t4 la hora de cierre; nula es el momento actual
 * @param t4Reason el motivo, si T4 es anterior al momento del registro
 * @param closureChecks las confirmaciones, solo de un equipo
 */
public record ClosureData(Instant t4, String t4Reason, ClosureResult result, UUID duplicateOfTicketId, CauseKind causeKind,
		String causeText, String recommendations, Map<String, String> closureChecks, String finalEvidenceWaiver,
		String closureCommunicationWaiver, String deviationJustification) {
}
