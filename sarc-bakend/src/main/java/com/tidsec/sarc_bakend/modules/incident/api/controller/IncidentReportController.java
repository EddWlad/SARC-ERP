package com.tidsec.sarc_bakend.modules.incident.api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.incident.api.response.IncidentSummaryResponse;
import com.tidsec.sarc_bakend.modules.incident.application.service.ControlReportService;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentManagementQuery;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentSummaryService;
import com.tidsec.sarc_bakend.modules.incident.application.service.ManagementReportService;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentReportQuery;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Informes de incidencias (contracts/reports.md). Los cinco roles los descargan; cada emision se archiva y devuelve
 * {@code X-Report-Id} y {@code X-Report-Cut-Off}. Sin {@code from} y {@code to} es «Todo el historial» (FR-072).
 */
@RestController
@RequestMapping("/api/v1/incidents")
class IncidentReportController {

	private final ControlReportService control;
	private final IncidentSummaryService summaries;
	private final ManagementReportService management;

	IncidentReportController(ControlReportService control, IncidentSummaryService summaries,
			ManagementReportService management) {
		this.control = control;
		this.summaries = summaries;
		this.management = management;
	}

	/**
	 * Cifras del tablero de gestion con las mismas reglas que el reporte (FR-080, FR-086). El periodo es obligatorio:
	 * la pantalla pide «Este mes» de inicio (D13).
	 */
	@GetMapping("/summary")
	IncidentSummaryResponse summary(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) ElementClass elementClass,
			@RequestParam(required = false) IncidentType incidentType, @RequestParam(required = false) Priority priority) {
		return IncidentSummaryResponse.from(summaries.figures(
				new IncidentManagementQuery.Selection(from, to, network == null ? null : network.name(), elementClass,
						incidentType, priority)));
	}

	/** «Gestion de incidencias» en PDF o Excel, con las mismas cifras que el tablero (FR-080 a FR-086). */
	@ApiResponse(responseCode = "200", description = "Documento nuevo; X-Report-Id y X-Report-Cut-Off lo identifican",
			headers = { @Header(name = "X-Report-Id", schema = @Schema(type = "string", format = "uuid")),
					@Header(name = "X-Report-Cut-Off", schema = @Schema(type = "string", format = "date-time")) },
			content = { @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
					@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
							schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/reports/management")
	ResponseEntity<byte[]> managementReport(@RequestParam ReportFormat format,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) ElementClass elementClass,
			@RequestParam(required = false) IncidentType incidentType, @RequestParam(required = false) Priority priority) {
		var generated = management.generate(new IncidentManagementQuery.Selection(from, to,
				network == null ? null : network.name(), elementClass, incidentType, priority), format);
		return download(generated.file(), generated.id(), generated.cutOff());
	}

	/** «Control de tickets» en PDF o Excel, con el estado de cada ticket al final del periodo (FR-070 a FR-077). */
	@ApiResponse(responseCode = "200", description = "Documento nuevo; X-Report-Id y X-Report-Cut-Off lo identifican",
			headers = { @Header(name = "X-Report-Id", schema = @Schema(type = "string", format = "uuid")),
					@Header(name = "X-Report-Cut-Off", schema = @Schema(type = "string", format = "date-time")) },
			content = { @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
					@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
							schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/reports/control")
	ResponseEntity<byte[]> controlReport(@RequestParam ReportFormat format,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) TicketState state, @RequestParam(required = false) ChargingNetwork network) {
		var generated = control.generate(
				new IncidentReportQuery.Selection(from, to, state, network == null ? null : network.name()), format);
		return download(generated.file(), generated.id(), generated.cutOff());
	}

	private static ResponseEntity<byte[]> download(ReportFile file, java.util.UUID id, java.time.Instant cutOff) {
		return ResponseEntity.ok().header("X-Report-Id", id.toString())
				.header("X-Report-Cut-Off", cutOff.toString())
				.cacheControl(CacheControl.noStore().cachePrivate())
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}
}
