package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.application.result.OpenMatchView;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un ticket abierto del mismo elemento (FR-016). */
public record OpenMatchResponse(UUID id, String code, String incidentTypeLabel, Priority priority,
		@Schema(nullable = true) Instant createdAt) {

	public static OpenMatchResponse from(OpenMatchView view) {
		return new OpenMatchResponse(view.id(), view.code(), view.incidentTypeLabel(), view.priority(),
				view.createdAt());
	}
}
