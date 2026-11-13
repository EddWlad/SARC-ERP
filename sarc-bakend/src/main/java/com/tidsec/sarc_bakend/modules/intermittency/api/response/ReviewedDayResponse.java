package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.ReviewedDayView;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un dia y su marca de revisado (FR-044). Un dia sin marca, o con la marca retirada, trae {@code active} falso;
 * {@code version} se envia al retirar la marca para detectar cambios concurrentes.
 */
public record ReviewedDayResponse(LocalDate day, boolean active, @Schema(nullable = true) UUID markedBy,
		@Schema(nullable = true) Instant markedAt, @Schema(nullable = true) Long version, boolean finished,
		int pendingLinkRows, int unconfirmedBatches) {

	public static ReviewedDayResponse from(ReviewedDayView view) {
		return new ReviewedDayResponse(view.day(), view.active(), view.markedBy(), view.markedAt(), view.version(),
				view.finished(), view.pendingLinkRows(), view.unconfirmedBatches());
	}
}
