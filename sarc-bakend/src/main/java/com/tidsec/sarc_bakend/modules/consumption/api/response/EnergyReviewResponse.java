package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record EnergyReviewResponse(EnergyReviewStatus decision, String justification, BigDecimal energyKwh,
		int sessionRevision, long sessionVersion, @Schema(nullable = true) UUID decidedBy, Instant decidedAt) {
	public static EnergyReviewResponse from(EnergyReview review) {
		return new EnergyReviewResponse(review.getDecision(), review.getJustification(), review.getEnergyKwh(),
				review.getSessionRevision(), review.getSessionVersion(), review.getDecidedBy(), review.getDecidedAt());
	}
}
