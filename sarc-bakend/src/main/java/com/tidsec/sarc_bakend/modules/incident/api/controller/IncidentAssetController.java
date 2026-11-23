package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.response.IncidentAssetResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentAssetQueryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Equipos activos para el formulario de creacion de tickets. */
@RestController
@RequestMapping("/api/v1/incidents/assets")
class IncidentAssetController {

	private final IncidentAssetQueryService assets;

	IncidentAssetController(IncidentAssetQueryService assets) {
		this.assets = assets;
	}

	@GetMapping
	List<IncidentAssetResponse> searchAssets(@RequestParam(defaultValue = "") @Size(max = 100) String search,
			@RequestParam(defaultValue = "20") @Min(1) @Max(IncidentAssetQueryService.MAX_RESULTS) int limit) {
		return assets.search(search, limit).stream().map(IncidentAssetResponse::from).toList();
	}
}
