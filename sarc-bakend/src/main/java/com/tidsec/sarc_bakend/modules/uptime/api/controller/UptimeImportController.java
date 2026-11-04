package com.tidsec.sarc_bakend.modules.uptime.api.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tidsec.sarc_bakend.modules.uptime.api.request.ConfirmUptimeImportRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.PasteUptimeImportRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.UptimeRowDecisionRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.response.UptimeImportBatchResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.UptimeImportRowResponse;
import com.tidsec.sarc_bakend.modules.uptime.application.command.UploadUptimeImportCommand;
import com.tidsec.sarc_bakend.modules.uptime.application.service.UptimeImportService;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.importing.UptimeRowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.web.VersionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Importacion de uptime por archivo o pegado, con la ventana consultada declarada. */
@RestController
@RequestMapping("/api/v1/uptime/imports")
class UptimeImportController {

	private final UptimeImportService imports;

	UptimeImportController(UptimeImportService imports) {
		this.imports = imports;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	UptimeImportBatchResponse upload(@RequestParam LocalDate weekStart, @RequestPart("file") MultipartFile file)
			throws IOException {
		return UptimeImportBatchResponse.from(imports.upload(
				new UploadUptimeImportCommand(weekStart, file.getOriginalFilename(), file.getBytes())));
	}

	@PostMapping("/paste")
	@ResponseStatus(HttpStatus.ACCEPTED)
	UptimeImportBatchResponse paste(@Valid @RequestBody PasteUptimeImportRequest body) {
		return UptimeImportBatchResponse.from(imports.paste(body.toCommand()));
	}

	@GetMapping
	PageResult<UptimeImportBatchResponse> history(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.history(new PageQuery(page, size)).map(UptimeImportBatchResponse::from);
	}

	@GetMapping("/{id}")
	UptimeImportBatchResponse get(@PathVariable UUID id) {
		return UptimeImportBatchResponse.from(imports.get(id));
	}

	@GetMapping("/{id}/rows")
	PageResult<UptimeImportRowResponse> rows(@PathVariable UUID id,
			@RequestParam(required = false) UptimeRowClassification classification,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.rows(id, classification, new PageQuery(page, size)).map(UptimeImportRowResponse::from);
	}

	@PutMapping("/{id}/rows/decision")
	List<UptimeImportRowResponse> decide(@PathVariable UUID id, @Valid @RequestBody UptimeRowDecisionRequest body) {
		return imports.decide(body.toCommand(id)).stream().map(UptimeImportRowResponse::from).toList();
	}

	@PostMapping("/{id}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	UptimeImportBatchResponse confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmUptimeImportRequest body) {
		return UptimeImportBatchResponse.from(imports.confirm(id, body.version(), body.wantsExact()));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	UptimeImportBatchResponse retry(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return UptimeImportBatchResponse.from(imports.retry(id, body.version()));
	}

	@PostMapping("/{id}/discard")
	UptimeImportBatchResponse discard(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return UptimeImportBatchResponse.from(imports.discard(id, body.version()));
	}
}
