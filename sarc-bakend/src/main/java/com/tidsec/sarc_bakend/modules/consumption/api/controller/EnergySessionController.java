package com.tidsec.sarc_bakend.modules.consumption.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.consumption.api.request.MarkTestRequest;
import com.tidsec.sarc_bakend.modules.consumption.api.response.PendingLinkResponse;
import com.tidsec.sarc_bakend.modules.consumption.api.response.SessionResponse;
import com.tidsec.sarc_bakend.modules.consumption.application.service.SessionQueryService;
import com.tidsec.sarc_bakend.modules.consumption.application.service.SessionService;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Historico de sesiones (PRD RF-003.11) y operaciones de calidad del dato. */
@RestController
@RequestMapping("/api/v1/energy/sessions")
class EnergySessionController {

	private final SessionQueryService queries;
	private final SessionService sessions;

	EnergySessionController(SessionQueryService queries, SessionService sessions) {
		this.queries = queries;
		this.sessions = sessions;
	}

	/** Periodo por dia de inicio: {@code from} inclusivo, {@code to} exclusivo. Sin estado: todas. */
	@GetMapping
	PageResult<SessionResponse> search(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) SessionSource source,
			@RequestParam(required = false) UUID equipmentId,
			@RequestParam(required = false) SessionState state,
			@RequestParam(required = false) @Size(max = 100) String text,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return queries.search(new SessionCriteria(from, to, network, type, source, equipmentId, state, text),
				new PageQuery(page, size)).map(SessionResponse::from);
	}

	/** Excluye o reincorpora una sesion de los indicadores oficiales, sin eliminarla. */
	@PatchMapping("/{id}/test")
	SessionResponse markTest(@PathVariable UUID id, @Valid @RequestBody MarkTestRequest body) {
		return SessionResponse.from(sessions.markTest(body.toCommand(id)));
	}

	/** Reintenta vincular las pendientes con las identidades verificadas del inventario. */
	@PostMapping("/link-pending")
	PendingLinkResponse linkPending() {
		return PendingLinkResponse.from(sessions.linkPending());
	}
}
