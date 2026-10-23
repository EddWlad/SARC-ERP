package com.tidsec.sarc_bakend.modules.inventory.api.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.inventory.api.request.CreateConnectorTypeRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.UpdateConnectorTypeRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.response.ConnectorTypeResponse;
import com.tidsec.sarc_bakend.modules.inventory.application.service.ConnectorTypeService;

@RestController
@RequestMapping("/api/v1/connector-types")
class ConnectorTypeController {

	private final ConnectorTypeService connectorTypes;

	ConnectorTypeController(ConnectorTypeService connectorTypes) {
		this.connectorTypes = connectorTypes;
	}

	@GetMapping
	List<ConnectorTypeResponse> list() {
		return connectorTypes.list().stream().map(ConnectorTypeResponse::from).toList();
	}

	@PostMapping
	ResponseEntity<ConnectorTypeResponse> create(@Valid @RequestBody CreateConnectorTypeRequest body) {
		ConnectorTypeResponse created = ConnectorTypeResponse.from(connectorTypes.create(body.code(), body.name()));
		return ResponseEntity.created(URI.create("/api/v1/connector-types/" + created.id())).body(created);
	}

	@PatchMapping("/{id}")
	ConnectorTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateConnectorTypeRequest body) {
		return ConnectorTypeResponse.from(connectorTypes.update(body.toCommand(id)));
	}
}
