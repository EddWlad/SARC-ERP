package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.time.Instant;
import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.application.result.TicketPageView;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una pagina del listado. {@code notice} es {@code TICKET_BEFORE_START} cuando se busco un codigo anterior al numero
 * inicial: ese ticket pertenece a la secuencia externa anterior y no esta registrado en SARC.
 */
public record TicketPageResponse(List<TicketSummaryResponse> items, long total, int page, int size, Instant cutOff,
		@Schema(nullable = true) String notice) {

	public static TicketPageResponse from(TicketPageView view) {
		return new TicketPageResponse(view.items().stream().map(TicketSummaryResponse::from).toList(), view.total(),
				view.page(), view.size(), view.cutOff(), view.notice());
	}
}
