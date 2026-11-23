package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.CloseRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.ReopenRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.application.command.CloseTicketCommand;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketLifecycleService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;

import jakarta.validation.Valid;

/** Cierre y reapertura de un ticket. Solo editores; responden con el detalle actualizado. */
@RestController
@RequestMapping("/api/v1/incidents/tickets/{id}")
class TicketLifecycleController {

	private final TicketLifecycleService lifecycle;
	private final TicketReadService read;

	TicketLifecycleController(TicketLifecycleService lifecycle, TicketReadService read) {
		this.lifecycle = lifecycle;
		this.read = read;
	}

	@PostMapping("/close")
	TicketDetailResponse closeTicket(@PathVariable UUID id, @Valid @RequestBody CloseRequest request) {
		lifecycle.close(new CloseTicketCommand(id, request.version(), request.t4(), request.t4Reason(), request.result(),
				request.duplicateOfTicketId(), request.causeKind(), request.causeText(), request.recommendations(),
				request.checksMap(), request.finalEvidenceWaiver(), request.closureCommunicationWaiver(),
				request.deviationJustification()));
		return TicketDetailResponse.from(read.detail(id));
	}

	@PostMapping("/reopen")
	TicketDetailResponse reopenTicket(@PathVariable UUID id, @Valid @RequestBody ReopenRequest request) {
		lifecycle.reopen(id, request.version(), request.reason(), request.t0(), request.t0Reason());
		return TicketDetailResponse.from(read.detail(id));
	}
}
