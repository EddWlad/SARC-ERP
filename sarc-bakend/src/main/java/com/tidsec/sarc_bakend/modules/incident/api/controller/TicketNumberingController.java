package com.tidsec.sarc_bakend.modules.incident.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.ConfigureNumberingRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.NumberingResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketNumberingService;

import jakarta.validation.Valid;

/** Numeracion de tickets: la lee cualquier rol autorizado y solo la configura un Administrador (FR-013). */
@RestController
@RequestMapping("/api/v1/incidents/numbering")
class TicketNumberingController {

	private final TicketNumberingService numbering;

	TicketNumberingController(TicketNumberingService numbering) {
		this.numbering = numbering;
	}

	@GetMapping
	NumberingResponse numberingState() {
		return NumberingResponse.from(numbering.view());
	}

	@PutMapping
	NumberingResponse configureNumbering(@Valid @RequestBody ConfigureNumberingRequest request) {
		return NumberingResponse.from(numbering.configure(request.toCommand()));
	}
}
