package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.response.EligibleResponsibleResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentResponsibleQueryService;

/** Usuarios elegibles como responsable o entrante de un relevo: editores activos, sin correo ni roles. */
@RestController
@RequestMapping("/api/v1/incidents/responsibles")
class IncidentResponsibleController {

	private final IncidentResponsibleQueryService responsibles;

	IncidentResponsibleController(IncidentResponsibleQueryService responsibles) {
		this.responsibles = responsibles;
	}

	@GetMapping
	List<EligibleResponsibleResponse> eligibleResponsibles() {
		return responsibles.eligible().stream().map(EligibleResponsibleResponse::from).toList();
	}
}
