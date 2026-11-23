package com.tidsec.sarc_bakend.modules.incident.api.request;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CauseKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureCheck;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /tickets/{id}/close} (FR-047). Todo salvo {@code version} es opcional: lo que falta se informa
 * como requisito pendiente en el 422 {@code TICKET_CLOSE_REQUIREMENTS}, con la lista completa de una vez.
 */
public record CloseRequest(
		@NotNull Long version,
		@Schema(nullable = true) Instant t4,
		@Schema(nullable = true) @Size(max = 300) String t4Reason,
		@Schema(nullable = true) ClosureResult result,
		@Schema(nullable = true) UUID duplicateOfTicketId,
		@Schema(nullable = true) CauseKind causeKind,
		@Schema(nullable = true) @Size(max = 500) String causeText,
		@Schema(nullable = true) @Size(max = 1000) String recommendations,
		@Schema(nullable = true) Checks closureChecks,
		@Schema(nullable = true) @Size(max = 300) String finalEvidenceWaiver,
		@Schema(nullable = true) @Size(max = 300) String closureCommunicationWaiver,
		@Schema(nullable = true) @Size(max = 500) String deviationJustification) {

	/** Las cuatro confirmaciones de un equipo; cada una «Confirmada» o «No aplica». */
	public record Checks(
			@Schema(nullable = true) ClosureCheck platformNormal,
			@Schema(nullable = true) ClosureCheck stabilityChecked,
			@Schema(nullable = true) ClosureCheck noAffectedUser,
			@Schema(nullable = true) ClosureCheck postTest) {

		Map<String, ClosureCheck> asMap() {
			Map<String, ClosureCheck> map = new LinkedHashMap<>();
			put(map, "platformNormal", platformNormal);
			put(map, "stabilityChecked", stabilityChecked);
			put(map, "noAffectedUser", noAffectedUser);
			put(map, "postTest", postTest);
			return map;
		}

		private static void put(Map<String, ClosureCheck> map, String key, ClosureCheck value) {
			if (value != null) {
				map.put(key, value);
			}
		}
	}

	public Map<String, ClosureCheck> checksMap() {
		return closureChecks == null ? Map.of() : closureChecks.asMap();
	}
}
