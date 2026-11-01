package com.tidsec.sarc_bakend.modules.reporting.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

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

	private final IssuedReportService reports;

	IssuedReportController(IssuedReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	PageResult<IssuedReportResponse> issuedReports(
			@RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,30}") String kind,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return reports.search(kind, new PageQuery(page, size)).map(report -> IssuedReportResponse.from(report, reports.canReadOperational()));
	}

	@ApiResponse(responseCode = "200", description = "Copia archivada exacta; nunca una URL del proveedor",
			content = { @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
					@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
							schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/{id}/download")
	ResponseEntity<byte[]> issuedReportDownload(@PathVariable UUID id) {
		var file = reports.download(id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.format().contentType()))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}
}
