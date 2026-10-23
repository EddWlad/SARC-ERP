package com.tidsec.sarc_bakend.modules.consumption.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.consumption.api.response.ConsumptionSummaryResponse;
import com.tidsec.sarc_bakend.modules.consumption.application.service.ConsumptionReportService;
import com.tidsec.sarc_bakend.modules.consumption.application.service.SessionQueryService;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.SessionCriteria;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Tablero y reportes de consumo (PRD 7.3). Ambos usan el mismo calculo con el mismo filtro; el
 * periodo es por dia de inicio, {@code from} inclusivo y {@code to} exclusivo.
 */
@RestController
@RequestMapping("/api/v1/energy")
class ConsumptionController {

	private final SessionQueryService queries;
	private final ConsumptionReportService reports;

	ConsumptionController(SessionQueryService queries, ConsumptionReportService reports) {
		this.queries = queries;
		this.reports = reports;
	}

	@GetMapping("/summary")
	ConsumptionSummaryResponse summary(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) SessionSource source,
			@RequestParam(required = false) UUID equipmentId) {
		return ConsumptionSummaryResponse.from(
				queries.summary(new SessionCriteria(from, to, network, type, source, equipmentId, null, null)));
	}

	/** PDF gerencial o Excel con desglose y transacciones. */
	@GetMapping("/report")
	ResponseEntity<byte[]> report(
			@RequestParam ReportFormat format,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) SessionSource source,
			@RequestParam(required = false) UUID equipmentId) {
		ReportFile file = reports.generate(
				new SessionCriteria(from, to, network, type, source, equipmentId, null, null), format);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}
}
