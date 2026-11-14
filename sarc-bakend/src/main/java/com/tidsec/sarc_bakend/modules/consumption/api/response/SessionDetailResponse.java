package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionDetail;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle de una sesion (Feature 009, FR-011 y FR-016). La baja y las revisiones solo se entregan a quien
 * lee datos operativos; para el resto llegan vacias.
 */
public record SessionDetailResponse(
		SessionResponse session,
		@Schema(nullable = true, description = "Motivo de la baja; solo para lectura operativa") String deletionReason,
		@Schema(nullable = true, description = "Usuario que dio de baja; solo para lectura operativa") UUID deletedBy,
		List<SessionRevisionEntry> revisions) {

	/** Version anterior de la sesion y el cambio que la reemplazo. */
	public record SessionRevisionEntry(
			int revision,
			SessionRevision.Kind kind,
			@Schema(nullable = true) String reason,
			Instant supersededAt,
			@Schema(nullable = true) UUID supersededBy,
			Instant startedAt,
			@Schema(nullable = true) Instant endedAt,
			@Schema(nullable = true) Integer sourceUtcOffsetMinutes,
			@Schema(nullable = true) Integer sourceEndUtcOffsetMinutes,
			@Schema(nullable = true) BigDecimal energyKwh,
			@Schema(nullable = true) SessionState state,
			@Schema(nullable = true) EnergyReviewStatus energyReview,
			@Schema(nullable = true) UUID equipmentId) {

		static SessionRevisionEntry from(SessionRevision revision) {
			return new SessionRevisionEntry(revision.getRevision(), revision.getKind(), revision.getReason(),
					revision.getSupersededAt(), revision.getSupersededBy(), revision.getStartedAt(),
					revision.getEndedAt(), revision.getSourceUtcOffsetMinutes(),
					revision.getSourceEndUtcOffsetMinutes(), revision.getEnergyKwh(), revision.getState(),
					revision.getEnergyReview(), revision.getEquipmentId());
		}
	}

	public static SessionDetailResponse from(SessionDetail detail) {
		EnergySession session = detail.view().session();
		boolean operational = detail.operational();
		return new SessionDetailResponse(SessionResponse.from(detail.view()),
				operational ? session.getDeletionReason() : null, operational ? session.getDeletedBy() : null,
				detail.revisions().stream().map(SessionRevisionEntry::from).toList());
	}
}
