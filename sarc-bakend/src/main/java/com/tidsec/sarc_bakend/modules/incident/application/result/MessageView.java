package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;

/** El texto del mensaje para WhatsApp y los campos que le faltan (ubicacion, enlace de Maps). */
public record MessageView(String text, List<String> missingFields, String ticketCode, TicketState state) {
}
