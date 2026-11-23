package com.tidsec.sarc_bakend.modules.incident.api.response;

import com.tidsec.sarc_bakend.modules.incident.application.result.NumberingView;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado de la numeracion de tickets (contracts/incidents-api.md, «Numeración»). */
public record NumberingResponse(boolean configured,
		@Schema(nullable = true) String environment,
		@Schema(nullable = true) Integer startNumber,
		@Schema(nullable = true) String nextCode,
		@Schema(nullable = true) String expectedEnvironment,
		boolean ticketsExist) {

	public static NumberingResponse from(NumberingView view) {
		return new NumberingResponse(view.configured(), view.environment(), view.startNumber(), view.nextCode(),
				view.expectedEnvironment(), view.ticketsExist());
	}
}
