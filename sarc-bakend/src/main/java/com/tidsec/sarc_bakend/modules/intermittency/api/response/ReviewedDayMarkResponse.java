package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.ReviewedDayView.Marked;

/**
 * El dia marcado y los lotes en vista previa que lo tocan: todavia no afectan la cobertura, asi que se avisa
 * (FR-045).
 */
public record ReviewedDayMarkResponse(ReviewedDayResponse day, List<UnconfirmedBatchResponse> warnings) {

	/** Un lote sin confirmar con filas de ese dia. */
	public record UnconfirmedBatchResponse(UUID batchId, String fileName) {
	}

	public static ReviewedDayMarkResponse from(Marked marked) {
		return new ReviewedDayMarkResponse(ReviewedDayResponse.from(marked.day()), marked.warnings().stream()
				.map(batch -> new UnconfirmedBatchResponse(batch.batchId(), batch.fileName())).toList());
	}
}
