package com.tidsec.sarc_bakend.modules.uptime.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.uptime.api.request.LinkRowRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.UpdateConnectorAliasRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ConnectorAliasResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.UptimeImportRowResponse;
import com.tidsec.sarc_bakend.modules.uptime.application.service.ConnectorAliasService;
import com.tidsec.sarc_bakend.modules.uptime.application.service.PendingLinkService;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Conciliacion de filas sin conector y alias aprobados. */
@RestController
@RequestMapping("/api/v1/uptime")
class UptimePendingLinkController {

	private final PendingLinkService links;
	private final ConnectorAliasService aliases;

	UptimePendingLinkController(PendingLinkService links, ConnectorAliasService aliases) {
		this.links = links;
		this.aliases = aliases;
	}

	@GetMapping("/pending-links")
	PageResult<UptimeImportRowResponse> pending(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return links.pending(new PageQuery(page, size)).map(UptimeImportRowResponse::from);
	}

	@PostMapping("/pending-links/{rowId}/link")
	UptimeImportRowResponse link(@PathVariable UUID rowId, @Valid @RequestBody LinkRowRequest body) {
		return UptimeImportRowResponse.from(links.link(rowId, body.version(), body.connectorId(), body.remembers()));
	}

	@GetMapping("/connector-aliases")
	PageResult<ConnectorAliasResponse> aliases(@RequestParam(required = false) UUID equipmentId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return aliases.search(equipmentId, new PageQuery(page, size)).map(ConnectorAliasResponse::from);
	}

	@PutMapping("/connector-aliases/{id}")
	ConnectorAliasResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateConnectorAliasRequest body) {
		return ConnectorAliasResponse.from(aliases.update(id, body.version(), body.connectorId(), body.active()));
	}
}
