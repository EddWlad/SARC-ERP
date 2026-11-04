package com.tidsec.sarc_bakend.modules.uptime.api.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.uptime.api.request.UptimeFilterParams;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ConnectorResultResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.EquipmentResultResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ExclusionResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ScopeConnectorResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.UptimeSummaryResponse;
import com.tidsec.sarc_bakend.modules.uptime.application.service.UptimeQueryService;
import com.tidsec.sarc_bakend.modules.uptime.application.service.UptimeReportService;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Indicadores de disponibilidad, abiertos a los cinco roles. La proyeccion se aplica en el servicio. */
@RestController
@RequestMapping("/api/v1/uptime")
class UptimeController {

	private final UptimeQueryService queries;
	private final UptimeReportService reports;

	UptimeController(UptimeQueryService queries, UptimeReportService reports) {
		this.queries = queries;
		this.reports = reports;
	}

	@GetMapping("/summary")
	UptimeSummaryResponse summary(@ParameterObject UptimeFilterParams filter) {
		return UptimeSummaryResponse.from(queries.view(filter.toFilter()));
	}

	@GetMapping("/results/connectors")
	PageResult<ConnectorResultResponse> connectors(@ParameterObject UptimeFilterParams filter,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return queries.connectorResults(filter.toFilter(), new PageQuery(page, size)).map(ConnectorResultResponse::from);
	}

	@GetMapping("/results/equipment")
	PageResult<EquipmentResultResponse> equipment(@ParameterObject UptimeFilterParams filter,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return queries.equipmentResults(filter.toFilter(), new PageQuery(page, size)).map(EquipmentResultResponse::from);
	}

	@GetMapping("/results/exclusions")
	PageResult<ExclusionResponse> exclusions(@ParameterObject UptimeFilterParams filter,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return queries.exclusions(filter.toFilter(), new PageQuery(page, size)).map(ExclusionResponse::from);
	}

	/** PDF o Excel con las cifras del tablero; cada emision se archiva. */
	@ApiResponse(responseCode = "200", description = "Documento nuevo; X-Report-Id y X-Report-Cut-Off lo identifican",
			headers = { @Header(name = "X-Report-Id", schema = @Schema(type = "string", format = "uuid")),
					@Header(name = "X-Report-Cut-Off", schema = @Schema(type = "string", format = "date-time")) },
			content = { @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
					@Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
							schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/report")
	ResponseEntity<byte[]> report(@RequestParam ReportFormat format, @ParameterObject UptimeFilterParams filter) {
		var generated = reports.generateIssued(filter.toFilter(), format);
		ReportFile file = generated.file();
		return ResponseEntity.ok()
				.header("X-Report-Id", generated.id().toString())
				.header("X-Report-Cut-Off", generated.cutOff().toString())
				.cacheControl(CacheControl.noStore().cachePrivate())
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}

	@GetMapping("/scope/connectors")
	List<ScopeConnectorResponse> scope(@RequestParam(required = false) String q) {
		return queries.scope(q).stream().map(ScopeConnectorResponse::from).toList();
	}
}
