package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.WindowView;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowSource;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.DurationText;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una ventana. La duracion confirmada y la provisional van en campos distintos: la de una ventana abierta nunca
 * se mezcla con la oficial (FR-012).
 */
public record WindowResponse(UUID id, long version, UUID equipmentId, String pointLabel, Instant startAt,
		@Schema(nullable = true) Instant endAt, @Schema(nullable = true) Integer durationMinutes,
		@Schema(nullable = true) String durationText, @Schema(nullable = true) Long provisionalMinutes,
		@Schema(nullable = true) String provisionalText, WindowState state, List<ReviewReason> reviewReasons,
		@Schema(nullable = true) String comment, WindowSource source, int revision) {

	public static WindowResponse from(WindowView view) {
		IntermittencyWindow window = view.window();
		Integer minutes = window.getDurationMinutes();
		Long provisional = view.provisionalMinutes();
		return new WindowResponse(window.getId(), window.getVersion(), window.getEquipmentId(),
				window.getPointLabel(), window.getStartAt(), window.getEndAt(), minutes,
				minutes == null ? null : DurationText.hoursAndMinutes(minutes), provisional,
				provisional == null ? null : DurationText.hoursAndMinutes(provisional), window.getState(),
				view.operational() ? window.getReviewReasons() : List.of(), window.getComment(), window.getSource(), window.getCurrentRevision());
	}
}
