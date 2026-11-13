package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.MarkPointTestRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.KiaPointResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.PointTestResultResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.KiaPointQueryService;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.WindowService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Puntos de carga KIA: los eligen la captura y el filtro del dashboard. */
@RestController
@RequestMapping("/api/v1/intermittency/points")
class IntermittencyPointController {

	private final KiaPointQueryService points;
	private final WindowService windows;

	IntermittencyPointController(KiaPointQueryService points, WindowService windows) {
		this.points = points;
		this.windows = windows;
	}

	/**
	 * Marca como prueba las ventanas CONFIRMED o REVIEW del punto en el rango; no es una marca permanente del
	 * punto (FR-016). Informa cuantas ventanas abiertas del rango quedaron sin marcar.
	 */
	@PostMapping("/{equipmentId}/test")
	PointTestResultResponse markTest(@PathVariable UUID equipmentId, @Valid @RequestBody MarkPointTestRequest request) {
		return PointTestResultResponse.from(windows.markPointTest(request.toCommand(equipmentId)));
	}

	/** Quita la marca de prueba de las ventanas del punto en el rango. */
	@PostMapping("/{equipmentId}/untest")
	PointTestResultResponse unmarkTest(@PathVariable UUID equipmentId,
			@Valid @RequestBody MarkPointTestRequest request) {
		return PointTestResultResponse.from(windows.unmarkPointTest(request.toCommand(equipmentId)));
	}

	/** Solo puntos KIA, por codigo o nombre; cualquier usuario autenticado (FR-032). */
	@GetMapping
	List<KiaPointResponse> search(@RequestParam(defaultValue = "") @Size(max = 100) String query,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return points.search(query, limit).stream().map(KiaPointResponse::from).toList();
	}
}
