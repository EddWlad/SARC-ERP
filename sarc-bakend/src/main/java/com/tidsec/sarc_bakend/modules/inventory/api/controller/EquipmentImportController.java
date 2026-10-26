package com.tidsec.sarc_bakend.modules.inventory.api.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import com.tidsec.sarc_bakend.modules.inventory.api.request.EquipmentRowDecisionRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.PasteEquipmentImportRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentImportBatchResponse;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentImportRowResponse;
import com.tidsec.sarc_bakend.modules.inventory.application.command.UploadEquipmentImportCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.service.EquipmentImportService;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.web.VersionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Carga masiva de equipos con la plantilla de SARC. Cargar, confirmar y reintentar responden 202:
 * el trabajo sigue en segundo plano y el cliente consulta el lote hasta que cambie de estado.
 */
@RestController
@RequestMapping("/api/v1/equipment/imports")
class EquipmentImportController {

	private final EquipmentImportService imports;

	EquipmentImportController(EquipmentImportService imports) {
		this.imports = imports;
	}

	/** Plantilla XLSX con las columnas, las listas de valores validos y las instrucciones. */
	@GetMapping("/template")
	ResponseEntity<byte[]> equipmentImportTemplate() {
		ReportFile file = imports.template();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	EquipmentImportBatchResponse uploadEquipmentImport(@RequestPart("file") MultipartFile file) throws IOException {
		return EquipmentImportBatchResponse.from(
				imports.upload(new UploadEquipmentImportCommand(file.getOriginalFilename(), file.getBytes())));
	}

	@PostMapping("/paste")
	@ResponseStatus(HttpStatus.ACCEPTED)
	EquipmentImportBatchResponse pasteEquipmentImport(@Valid @RequestBody PasteEquipmentImportRequest body) {
		return EquipmentImportBatchResponse.from(imports.paste(body.toCommand()));
	}

	@GetMapping
	PageResult<EquipmentImportBatchResponse> equipmentImportHistory(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.history(new PageQuery(page, size)).map(EquipmentImportBatchResponse::from);
	}

	@GetMapping("/{id}")
	EquipmentImportBatchResponse equipmentImport(@PathVariable UUID id) {
		return EquipmentImportBatchResponse.from(imports.get(id));
	}

	@GetMapping("/{id}/rows")
	PageResult<EquipmentImportRowResponse> equipmentImportRows(@PathVariable UUID id,
			@RequestParam(required = false) EquipmentRowClassification classification,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.rows(id, classification, new PageQuery(page, size)).map(EquipmentImportRowResponse::from);
	}

	@PutMapping("/{id}/rows/decision")
	List<EquipmentImportRowResponse> decideEquipmentImportRows(@PathVariable UUID id, @Valid @RequestBody EquipmentRowDecisionRequest body) {
		return imports.decide(body.toCommand(id)).stream().map(EquipmentImportRowResponse::from).toList();
	}

	@PostMapping("/{id}/confirm")
	@ResponseStatus(HttpStatus.ACCEPTED)
	EquipmentImportBatchResponse confirmEquipmentImport(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return EquipmentImportBatchResponse.from(imports.confirm(id, body.version()));
	}

	@PostMapping("/{id}/retry")
	@ResponseStatus(HttpStatus.ACCEPTED)
	EquipmentImportBatchResponse retryEquipmentImport(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return EquipmentImportBatchResponse.from(imports.retry(id, body.version()));
	}

	@PostMapping("/{id}/discard")
	EquipmentImportBatchResponse discardEquipmentImport(@PathVariable UUID id, @Valid @RequestBody VersionRequest body) {
		return EquipmentImportBatchResponse.from(imports.discard(id, body.version()));
	}
}
