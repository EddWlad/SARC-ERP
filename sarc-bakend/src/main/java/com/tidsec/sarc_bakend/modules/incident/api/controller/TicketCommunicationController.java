package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.CommunicationRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.CommunicationService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;

import jakarta.validation.Valid;

/** Registro del envio real de un aviso publicado a mano. Solo editores; responde con el detalle actualizado. */
@RestController
@RequestMapping("/api/v1/incidents/tickets/{id}/communications")
class TicketCommunicationController {

	private final CommunicationService communications;
	private final TicketReadService read;

	TicketCommunicationController(CommunicationService communications, TicketReadService read) {
		this.communications = communications;
		this.read = read;
	}

	@PostMapping
	TicketDetailResponse recordCommunication(@PathVariable UUID id, @Valid @RequestBody CommunicationRequest request) {
		communications.record(id, request.version(), request.channel(), request.channelDetail(), request.kind(),
				request.sentAt(), request.justification());
		return TicketDetailResponse.from(read.detail(id));
	}
}
