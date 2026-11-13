package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.CaptureWindowsRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.CloseWindowRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.CorrectWindowRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.WindowChangeRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.CaptureResultResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.WindowDetailResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.WindowPageResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.WindowResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.WindowReadService;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.WindowService;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Ventanas de intermitencia de los puntos KIA. */
@RestController
@RequestMapping("/api/v1/intermittency/windows")
class IntermittencyWindowController {

	private final WindowService windows;
	private final WindowReadService reads;
	private final OperatingZone zone;

	IntermittencyWindowController(WindowService windows, WindowReadService reads, OperatingZone zone) {
		this.windows = windows;
		this.reads = reads;
		this.zone = zone;
	}

	/**
	 * Historial paginado. {@code from} y {@code to} son dias en la zona operativa, con el fin exclusivo. Quien no
	 * es editor recibe solo las ventanas CONFIRMED, pida los estados que pida (FR-005).
	 */
	@GetMapping
	WindowPageResponse list(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) List<UUID> equipmentIds,
			@RequestParam(required = false) List<WindowState> states, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return WindowPageResponse.from(reads.list(equipmentIds, from == null ? null : zone.startOf(from),
				to == null ? null : zone.startOf(to), states, new PageQuery(page, size)));
	}

	/** Detalle con el historial de revisiones; quien no es editor solo ve ventanas confirmadas (FR-005). */
	@GetMapping("/{id}")
	WindowDetailResponse detail(@PathVariable UUID id) {
		return WindowDetailResponse.from(reads.detail(id));
	}

	/** Captura de un punto: atomica, con tantas ventanas como se necesiten (FR-010, FR-019). */
	@PostMapping
	ResponseEntity<CaptureResultResponse> capture(@Valid @RequestBody CaptureWindowsRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(CaptureResultResponse.from(windows.capture(request.toCommand())));
	}

	@PatchMapping("/{id}")
	WindowResponse correct(@PathVariable UUID id, @Valid @RequestBody CorrectWindowRequest request) {
		return WindowResponse.from(windows.correct(request.toCommand(id)));
	}

	@PostMapping("/{id}/confirm-review")
	WindowResponse confirmReview(@PathVariable UUID id, @Valid @RequestBody WindowChangeRequest request) {
		return WindowResponse.from(windows.confirmReview(request.toCommand(id)));
	}

	@PostMapping("/{id}/test")
	WindowResponse markTest(@PathVariable UUID id, @Valid @RequestBody WindowChangeRequest request) {
		return WindowResponse.from(windows.markTest(request.toCommand(id)));
	}

	@PostMapping("/{id}/untest")
	WindowResponse unmarkTest(@PathVariable UUID id, @Valid @RequestBody WindowChangeRequest request) {
		return WindowResponse.from(windows.unmarkTest(request.toCommand(id)));
	}

	@PostMapping("/{id}/annul")
	WindowResponse annul(@PathVariable UUID id, @Valid @RequestBody WindowChangeRequest request) {
		return WindowResponse.from(windows.annul(request.toCommand(id)));
	}

	@PostMapping("/{id}/close")
	WindowResponse close(@PathVariable UUID id, @Valid @RequestBody CloseWindowRequest request) {
		return WindowResponse.from(windows.close(request.toCommand(id)));
	}
}
