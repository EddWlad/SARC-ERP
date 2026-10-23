package com.tidsec.sarc_bakend.modules.consumption.api.controller;

import java.io.IOException;
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

import com.tidsec.sarc_bakend.modules.consumption.api.request.PasteImportRequest;
import com.tidsec.sarc_bakend.modules.consumption.api.request.RowDecisionRequest;
import com.tidsec.sarc_bakend.modules.consumption.api.response.ImportBatchResponse;
import com.tidsec.sarc_bakend.modules.consumption.api.response.ImportRowResponse;
import com.tidsec.sarc_bakend.modules.consumption.application.command.UploadImportCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.service.SessionImportService;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.web.VersionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Importacion de sesiones de carga (PRD 3.1, RF-003.1 a 003.9). Cargar y confirmar responden 202:
 * el trabajo sigue en segundo plano y el cliente consulta el lote hasta que cambie de estado.
 */
@RestController
@RequestMapping("/api/v1/energy/imports")
class EnergyImportController {

	private final SessionImportService imports;

	EnergyImportController(SessionImportService imports) {
		this.imports = imports;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	ImportBatchResponse upload(@RequestParam SessionSource source,
			@RequestParam(required = false) @Size(max = 100) String scope,
			@RequestPart("file") MultipartFile file) throws IOException {
		return ImportBatchResponse.from(imports.upload(
				new UploadImportCommand(source, scope, file.getOriginalFilename(), file.getBytes())));
	}

	@PostMapping("/paste")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ImportBatchResponse paste(@Valid @RequestBody PasteImportRequest body) {
		return ImportBatchResponse.from(imports.paste(body.toCommand()));
	}

	@GetMapping
	PageResult<ImportBatchResponse> history(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.history(new PageQuery(page, size)).map(ImportBatchResponse::from);
	}

	@GetMapping("/{id}")
	ImportBatchResponse get(@PathVariable UUID id) {
		return ImportBatchResponse.from(imports.get(id));
	}

	@GetMapping("/{id}/rows")
	PageResult<ImportRowResponse> rows(@PathVariable UUID id,
			@RequestParam(required = false) RowClassification classification,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.rows(id, classification, new PageQuery(page, size)).map(ImportRowResponse::from);
	}

	@PutMapping("/{id}/rows/decision")
	List<ImportRowResponse> decide(@PathVariable UUID id, @Valid @RequestBody RowDecisionRequest body) {
		return imports.decide(body.toCommand(id)).stream().map(ImportRowResponse::from).toList();
	}

	@PostMapping("/{id}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ImportBatchResponse confirm(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return ImportBatchResponse.from(imports.confirm(id, body.version()));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	ImportBatchResponse retry(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return ImportBatchResponse.from(imports.retry(id, body.version()));
	}

	@PostMapping("/{id}/discard")
	ImportBatchResponse discard(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return ImportBatchResponse.from(imports.discard(id, body.version()));
	}
}
