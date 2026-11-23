package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.request.AddRelationRequest;
import com.tidsec.sarc_bakend.modules.incident.api.request.CreateTicketRequest;
import com.tidsec.sarc_bakend.modules.incident.api.response.OpenMatchResponse;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketDetailResponse;
import com.tidsec.sarc_bakend.modules.incident.api.response.TicketPageResponse;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketCreation;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketCreationService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketReadService;
import com.tidsec.sarc_bakend.modules.incident.application.service.TicketRelationService;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketListQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tickets: listado, detalle, creacion y relaciones (contracts/incidents-api.md). La autorizacion esta en cada caso de
 * uso: crear y relacionar son de los editores; consultar, de los cinco roles.
 */
@RestController
@RequestMapping("/api/v1/incidents/tickets")
class TicketController {

	/** {@code campo} o {@code campo,asc|desc}. El elemento afectado no se ordena. */
	private static final String SORT_PATTERN = "^(code|state|type|network|priority|responsible|t0|lastActivityAt)(,(asc|desc))?$";

	private final TicketReadService read;
	private final TicketCreationService creation;
	private final TicketRelationService relations;

	TicketController(TicketReadService read, TicketCreationService creation, TicketRelationService relations) {
		this.read = read;
		this.creation = creation;
		this.relations = relations;
	}

	@GetMapping
	TicketPageResponse listTickets(@RequestParam(required = false) TicketState state,
			@RequestParam(required = false) ElementClass elementClass,
			@RequestParam(required = false) IncidentType incidentType,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) @Pattern(regexp = "^(KIA|JAC|CONECTZ|NONE)$") String network,
			@RequestParam(required = false) UUID equipmentId, @RequestParam(required = false) UUID responsibleId,
			@RequestParam(required = false) TicketCondition condition,
			@RequestParam(required = false) @Size(max = 100) String search,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "lastActivityAt,desc") @Pattern(regexp = SORT_PATTERN) String sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return TicketPageResponse.from(read.list(new TicketListQuery(state, elementClass, incidentType, priority,
				network, equipmentId, responsibleId, condition, search, parseSort(sort), page, size), from, to));
	}

	/** Tickets abiertos del mismo elemento, para avisar antes de enviar el formulario (FR-016). */
	@GetMapping("/open-matches")
	List<OpenMatchResponse> openMatches(@RequestParam ElementClass elementClass,
			@RequestParam(required = false) UUID equipmentId,
			@RequestParam(required = false) SiteElementKind siteElementKind,
			@RequestParam(required = false) UUID referenceEquipmentId,
			@RequestParam(required = false) @Size(max = 150) String manualSiteName,
			@RequestParam(required = false) @Size(max = 120) String elementName) {
		return read.openMatches(elementClass, equipmentId, siteElementKind, referenceEquipmentId, manualSiteName,
				elementName).stream().map(OpenMatchResponse::from).toList();
	}

	@GetMapping("/{id}")
	TicketDetailResponse ticketDetail(@PathVariable UUID id) {
		return TicketDetailResponse.from(read.detail(id));
	}

	/** 201 con el ticket creado, o 200 con el mismo ticket si el envio se repitio (idempotencia). */
	@PostMapping
	ResponseEntity<TicketDetailResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
		TicketCreation created = creation.create(request.toCommand());
		TicketDetailResponse body = TicketDetailResponse.from(read.detail(created.ticketId()));
		if (!created.created()) {
			return ResponseEntity.ok(body);
		}
		return ResponseEntity.created(URI.create("/api/v1/incidents/tickets/" + created.ticketId())).body(body);
	}

	@PostMapping("/{id}/relations")
	TicketDetailResponse addRelation(@PathVariable UUID id, @Valid @RequestBody AddRelationRequest request) {
		relations.add(id, request.version(), request.relatedTicketId(), request.kind(), request.reason());
		return TicketDetailResponse.from(read.detail(id));
	}

	@DeleteMapping("/{id}/relations/{relatedId}")
	TicketDetailResponse removeRelation(@PathVariable UUID id, @PathVariable UUID relatedId,
			@RequestParam long version) {
		relations.remove(id, version, relatedId);
		return TicketDetailResponse.from(read.detail(id));
	}

	private static TicketListQuery.Sort parseSort(String sort) {
		String[] parts = sort.split(",");
		boolean descending = parts.length > 1 && "desc".equals(parts[1]);
		TicketListQuery.SortField field = switch (parts[0]) {
			case "code" -> TicketListQuery.SortField.CODE;
			case "state" -> TicketListQuery.SortField.STATE;
			case "type" -> TicketListQuery.SortField.TYPE;
			case "network" -> TicketListQuery.SortField.NETWORK;
			case "priority" -> TicketListQuery.SortField.PRIORITY;
			case "responsible" -> TicketListQuery.SortField.RESPONSIBLE;
			case "t0" -> TicketListQuery.SortField.T0;
			default -> TicketListQuery.SortField.LAST_ACTIVITY;
		};
		return new TicketListQuery.Sort(field, descending);
	}
}
