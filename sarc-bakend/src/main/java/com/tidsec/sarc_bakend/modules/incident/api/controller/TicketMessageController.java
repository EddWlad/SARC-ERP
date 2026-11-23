package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.response.MessageResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketMessageService;

/**
 * Mensaje para WhatsApp de un ticket. Solo lectura: no crea eventos ni comunicaciones, porque copiar el texto no
 * demuestra que se haya enviado (FR-059). Lo pueden pedir los cinco roles.
 */
@RestController
@RequestMapping("/api/v1/incidents/tickets/{id}/message")
class TicketMessageController {

	private final TicketMessageService messages;

	TicketMessageController(TicketMessageService messages) {
		this.messages = messages;
	}

	@GetMapping
	MessageResponse ticketMessage(@PathVariable UUID id,
			@RequestParam(defaultValue = "false") boolean includeDetection,
			@RequestParam(defaultValue = "false") boolean includeActions) {
		return MessageResponse.from(messages.compose(id, includeDetection, includeActions));
	}
}
