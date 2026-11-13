package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.IntermittencyFilterParams;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.AvailabilityResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.IntermittencySummaryResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.AvailabilityService;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencyReportService;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencySummaryService;
import com.tidsec.sarc_bakend.shared.error.OperationalAccessDeniedException;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

/** Dashboard de intermitencias: cifras del periodo y los puntos elegidos, para cualquier usuario autenticado. */
@RestController
@RequestMapping("/api/v1/intermittency")
class IntermittencyController {

	private final IntermittencySummaryService summaries;

	private final IntermittencyReportService reports;

	private final AvailabilityService availability;

	private final OperatingZone zone;

	IntermittencyController(IntermittencySummaryService summaries, IntermittencyReportService reports,
			AvailabilityService availability, OperatingZone zone) {
		this.summaries = summaries;
		this.reports = reports;
		this.availability = availability;
		this.zone = zone;
	}

	/**
	 * Indicadores oficiales, lo que queda fuera de ellos, Top 10, serie diaria y pasteles (FR-031 a FR-037).
	 * Nunca incluye disponibilidad estimada (FR-006). {@code to} es exclusivo.
	 */
	@GetMapping("/summary")
	IntermittencySummaryResponse summary(@ParameterObject @Valid IntermittencyFilterParams filter) {
		return IntermittencySummaryResponse.from(summaries.summary(filter.toQuery()), zone.zone().getId());
	}

	/**
	 * Disponibilidad estimada de prueba para los mismos filtros (FR-042 a FR-046a). Solo la reciben quienes tienen
	 * el permiso de disponibilidad (FR-006); los demas roles reciben 403 {@code AVAILABILITY_RESTRICTED}, aunque
	 * pidan la ruta directamente. Es una estimacion con los dias revisados como cobertura, no una medicion de
	 * Enerlink ni de Uptime.
	 */
	@GetMapping("/availability")
	AvailabilityResponse availability(@ParameterObject @Valid IntermittencyFilterParams filter) {
		try {
			return AvailabilityResponse.from(availability.estimate(filter.toQuery()), zone.zone().getId());
		}
		catch (AccessDeniedException denied) {
			throw new OperationalAccessDeniedException("AVAILABILITY_RESTRICTED",
					"La disponibilidad estimada no está disponible para tus roles");
		}
	}

	/**
	 * PDF gerencial o Excel con los mismos filtros, corte y cifras del dashboard (FR-038 a FR-041). Cada emision se
	 * archiva con su huella. Quien no edita recibe un informe solo con lo oficial; el anexo operativo es de editores.
	 * No se envia por correo: se descarga.
	 */
	@ApiResponse(responseCode = "200", description = "Documento nuevo; X-Report-Id y X-Report-Cut-Off lo identifican",
			headers = { @Header(name = "X-Report-Id", schema = @Schema(type = "string", format = "uuid")),
					@Header(name = "X-Report-Cut-Off", schema = @Schema(type = "string", format = "date-time")) },
			content = { @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
					@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
							schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/report")
	ResponseEntity<byte[]> report(@RequestParam ReportFormat format,
			@ParameterObject @Valid IntermittencyFilterParams filter) {
		var generated = reports.generateIssued(filter.toQuery(), format);
		ReportFile file = generated.file();
		return ResponseEntity.ok().header("X-Report-Id", generated.id().toString())
				.header("X-Report-Cut-Off", generated.cutOff().toString())
				.cacheControl(CacheControl.noStore().cachePrivate())
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}
}
