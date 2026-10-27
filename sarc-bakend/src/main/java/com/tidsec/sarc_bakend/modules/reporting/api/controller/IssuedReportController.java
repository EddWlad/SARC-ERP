package com.tidsec.sarc_bakend.modules.reporting.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.reporting.api.response.IssuedReportResponse;
import com.tidsec.sarc_bakend.modules.reporting.application.service.IssuedReportService;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Reportes emitidos (PRD RF-RPT-08): historial y descarga de la copia tal como se entrego. */
@RestController
@RequestMapping("/api/v1/reports/issued")
class IssuedReportController {

	/** URL firmada de la copia archivada; caduca, por eso se pide cada vez. */
	record DownloadResponse(String url) {
	}

	private final IssuedReportService reports;

	IssuedReportController(IssuedReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	PageResult<IssuedReportResponse> issuedReports(
			@RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,30}") String kind,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return reports.search(kind, new PageQuery(page, size)).map(IssuedReportResponse::from);
	}

	@GetMapping("/{id}/download")
	DownloadResponse issuedReportDownload(@PathVariable UUID id) {
		return new DownloadResponse(reports.downloadUrl(id));
	}
}
