package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowChange;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowRevision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una revision del historial: los valores de ese momento, quien cambio, cuando y por que (FR-017). */
public record WindowRevisionResponse(int revision, WindowChange change, Instant startAt,
		@Schema(nullable = true) Instant endAt, @Schema(nullable = true) Integer durationMinutes,
		WindowState state, List<ReviewReason> reviewReasons, @Schema(nullable = true) String comment,
		@Schema(nullable = true) String justification, UUID actorId, Instant at) {

	public static WindowRevisionResponse from(WindowRevision revision) {
		return new WindowRevisionResponse(revision.getRevision(), revision.getChange(), revision.getStartAt(),
				revision.getEndAt(), revision.getDurationMinutes(), revision.getState(),
				revision.getReviewReasons(), revision.getComment(), revision.getJustification(),
				revision.getActorId(), revision.getAt());
	}
}
