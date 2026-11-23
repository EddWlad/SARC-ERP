package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.VisitRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.VisitStageRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;
import com.tidsec.sarc_bakend.modules.incident.application.service.VisitService;

import jakarta.validation.Valid;

/**
 * Visitas tecnicas registradas a mano (P-08) y la sugerencia de proveedores ya usados. Responden con el detalle
 * actualizado del ticket; la autorizacion, solo editores, esta en {@link VisitService}.
 */
@RestController
@RequestMapping("/api/v1/incidents")
class TicketVisitController {

	private final VisitService visits;
	private final TicketReadService read;

	TicketVisitController(VisitService visits, TicketReadService read) {
		this.visits = visits;
		this.read = read;
	}

	@PostMapping("/tickets/{id}/visits")
	TicketDetailResponse requestVisit(@PathVariable UUID id, @Valid @RequestBody VisitRequest request) {
		visits.request(id, request.version(), request.providerName(), request.occurredAt(), request.note());
		return TicketDetailResponse.from(read.detail(id));
	}

	@PostMapping("/tickets/{id}/visits/{visitId}/stage")
	TicketDetailResponse moveVisitStage(@PathVariable UUID id, @PathVariable UUID visitId,
			@Valid @RequestBody VisitStageRequest request) {
		visits.moveStage(id, request.version(), visitId, request.stage(), request.occurredAt(), request.note(),
				request.providerName(), request.cancelDecidedBy(), request.cancelEvidenceNote(),
				request.reescalationCondition());
		return TicketDetailResponse.from(read.detail(id));
	}

	@GetMapping("/providers")
	List<String> visitProviders(@RequestParam(required = false) String search) {
		return visits.providers(search);
	}
}
