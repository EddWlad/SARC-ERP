package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.util.List;

/**
 * Una pagina del listado. {@code notice} es {@code TICKET_BEFORE_START} cuando se busco un codigo anterior al numero
 * inicial configurado: ese ticket pertenece a la secuencia externa anterior y no esta en SARC.
 */
public record TicketPageView(List<TicketSummaryView> items, long total, int page, int size, Instant cutOff,
		String notice) {

	public static final String NOTICE_BEFORE_START = "TICKET_BEFORE_START";
}
