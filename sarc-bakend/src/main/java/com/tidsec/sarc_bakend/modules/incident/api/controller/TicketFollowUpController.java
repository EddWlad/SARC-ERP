package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.AnnulRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.CorrectDataRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.EventRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.HandoverRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.MilestoneRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.PriorityRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.ReclassifyRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.ResponsibleRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.MilestoneService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketFollowUpService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;

import jakarta.validation.Valid;

/**
 * Seguimiento de un ticket: hitos, eventos manuales, prioridad, responsable, relevo, reclasificacion y correccion de
 * datos (contracts/incidents-api.md). Todas las acciones responden con el detalle actualizado; la autorizacion esta en
 * cada caso de uso y es solo de editores.
 */
@RestController
@RequestMapping("/api/v1/incidents/tickets/{id}")
class TicketFollowUpController {

	private final MilestoneService milestones;
	private final TicketFollowUpService followUp;
	private final TicketReadService read;

	TicketFollowUpController(MilestoneService milestones, TicketFollowUpService followUp, TicketReadService read) {
		this.milestones = milestones;
		this.followUp = followUp;
		this.read = read;
	}

	@PutMapping("/episodes/{ordinal}/milestones/{kind}")
	TicketDetailResponse recordMilestone(@PathVariable UUID id, @PathVariable int ordinal,
			@PathVariable MilestoneKind kind, @Valid @RequestBody MilestoneRequest request) {
		milestones.apply(id, request.version(), ordinal, kind, request.status(), request.occurredAt(), request.reason(),
				request.justification(), Boolean.TRUE.equals(request.orderWarningAck()));
		return detail(id);
	}

	@PostMapping("/events")
	TicketDetailResponse addEvent(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
		followUp.addEvent(id, request.version(), request.type(), request.occurredAt(), request.text(),
				request.targetKind(), request.targetRoleOrProvider());
		return detail(id);
	}

	@PostMapping("/events/{eventId}/annul")
	TicketDetailResponse annulEvent(@PathVariable UUID id, @PathVariable UUID eventId,
			@Valid @RequestBody AnnulRequest request) {
		followUp.annulEvent(id, request.version(), eventId, request.reason());
		return detail(id);
	}

	@PostMapping("/priority")
	TicketDetailResponse changePriority(@PathVariable UUID id, @Valid @RequestBody PriorityRequest request) {
		followUp.changePriority(id, request.version(), request.priority(), request.reason());
		return detail(id);
	}

	@PostMapping("/responsible")
	TicketDetailResponse assignResponsible(@PathVariable UUID id, @Valid @RequestBody ResponsibleRequest request) {
		followUp.assignResponsible(id, request.version(), request.responsibleId());
		return detail(id);
	}

	@PostMapping("/handover")
	TicketDetailResponse handover(@PathVariable UUID id, @Valid @RequestBody HandoverRequest request) {
		followUp.handover(id, request.version(), request.incomingResponsibleId(), request.currentStatus(),
				request.pending(), request.nextCheckAt(), request.closureCriterion());
		return detail(id);
	}

	@PostMapping("/reclassify")
	TicketDetailResponse reclassify(@PathVariable UUID id, @Valid @RequestBody ReclassifyRequest request) {
		followUp.reclassify(id, request.version(), request.incidentType(), request.reason());
		return detail(id);
	}

	@PatchMapping
	TicketDetailResponse correctData(@PathVariable UUID id, @Valid @RequestBody CorrectDataRequest request) {
		followUp.correctData(id, request.version(), request.description(), request.reportOrigin(),
				request.reportOriginDetail(), request.justification());
		return detail(id);
	}

	private TicketDetailResponse detail(UUID id) {
		return TicketDetailResponse.from(read.detail(id));
	}
}
