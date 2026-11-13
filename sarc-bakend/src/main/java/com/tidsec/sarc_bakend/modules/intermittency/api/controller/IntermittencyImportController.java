package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.EditImportRowRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.ImportRowDecisionRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.LinkImportRowRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.PasteIntermittencyImportRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.ResolvePendingRowRequest;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.IntermittencyImportBatchResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.IntermittencyImportRowResponse;
import com.tidsec.sarc_bakend.modules.intermittency.api.response.RowChangeResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencyImportService;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.web.VersionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Importacion del registro operativo con vista previa editable (FR-022 a FR-030). */
@RestController
@RequestMapping("/api/v1/intermittency/import-batches")
class IntermittencyImportController {

	private final IntermittencyImportService imports;

	IntermittencyImportController(IntermittencyImportService imports) {
		this.imports = imports;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	IntermittencyImportBatchResponse upload(@RequestPart("file") MultipartFile file) throws IOException {
		return IntermittencyImportBatchResponse
				.from(imports.upload(new ImportCommands.Upload(file.getOriginalFilename(), file.getBytes())));
	}

	@PostMapping("/paste")
	@ResponseStatus(HttpStatus.ACCEPTED)
	IntermittencyImportBatchResponse paste(@Valid @RequestBody PasteIntermittencyImportRequest body) {
		return IntermittencyImportBatchResponse.from(imports.paste(body.toCommand()));
	}

	@GetMapping
	PageResult<IntermittencyImportBatchResponse> history(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.history(new PageQuery(page, size)).map(IntermittencyImportBatchResponse::from);
	}

	@GetMapping("/{id}")
	IntermittencyImportBatchResponse get(@PathVariable UUID id) {
		return IntermittencyImportBatchResponse.from(imports.get(id));
	}

	@GetMapping("/{id}/rows")
	PageResult<IntermittencyImportRowResponse> rows(@PathVariable UUID id,
			@RequestParam(required = false) RowClassification classification,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.rows(id, classification, new PageQuery(page, size))
				.map(IntermittencyImportRowResponse::from);
	}

	@PatchMapping("/{id}/rows/{rowId}")
	RowChangeResponse edit(@PathVariable UUID id, @PathVariable UUID rowId,
			@Valid @RequestBody EditImportRowRequest body) {
		return RowChangeResponse.from(imports.editRow(body.toCommand(id, rowId)));
	}

	@PostMapping("/{id}/rows/{rowId}/link")
	RowChangeResponse link(@PathVariable UUID id, @PathVariable UUID rowId,
			@Valid @RequestBody LinkImportRowRequest body) {
		return RowChangeResponse.from(imports.linkRow(body.toCommand(id, rowId)));
	}

	@PostMapping("/{id}/rows/{rowId}/decision")
	RowChangeResponse decide(@PathVariable UUID id, @PathVariable UUID rowId,
			@Valid @RequestBody ImportRowDecisionRequest body) {
		return RowChangeResponse.from(imports.decide(body.toCommand(id, rowId)));
	}

	/** Resuelve una fila pendiente de un lote ya confirmado (FR-028). */
	@PostMapping("/{id}/rows/{rowId}/resolve")
	RowChangeResponse resolve(@PathVariable UUID id, @PathVariable UUID rowId,
			@Valid @RequestBody ResolvePendingRowRequest body) {
		return RowChangeResponse.from(imports.resolve(body.toCommand(id, rowId)));
	}

	@PostMapping("/{id}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	IntermittencyImportBatchResponse confirm(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return IntermittencyImportBatchResponse.from(imports.confirm(id, body.version()));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	IntermittencyImportBatchResponse retry(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return IntermittencyImportBatchResponse.from(imports.retry(id, body.version()));
	}

	@PostMapping("/{id}/discard")
	IntermittencyImportBatchResponse discard(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return IntermittencyImportBatchResponse.from(imports.discard(id, body.version()));
	}
}
