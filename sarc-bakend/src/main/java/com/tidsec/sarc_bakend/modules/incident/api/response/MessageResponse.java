package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.application.result.MessageView;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/**
 * Texto del mensaje para WhatsApp. {@code missingFields} dice que dato falta ({@code location}, {@code mapsUrl}); la
 * copia nunca se bloquea por eso.
 */
public record MessageResponse(String text, List<String> missingFields, String ticketCode, TicketState state) {

	public static MessageResponse from(MessageView view) {
		return new MessageResponse(view.text(), view.missingFields(), view.ticketCode(), view.state());
	}
}
