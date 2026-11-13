package com.tidsec.sarc_bakend.modules.intermittency.api.controller;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.intermittency.api.response.PendingRowResponse;
import com.tidsec.sarc_bakend.modules.intermittency.application.service.IntermittencyImportService;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Filas pendientes de resolver de lotes ya confirmados: punto por vincular o conflicto (FR-028). */
@RestController
@RequestMapping("/api/v1/intermittency/pending-rows")
class IntermittencyPendingRowController {

	private final IntermittencyImportService imports;

	IntermittencyPendingRowController(IntermittencyImportService imports) {
		this.imports = imports;
	}

	/** {@code from} y {@code to} son dias locales, ambos incluidos: filtran por los dias que toca cada fila. */
	@GetMapping
	PageResult<PendingRowResponse> list(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) RowClassification classification,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return imports.pendingRows(from, to, classification, new PageQuery(page, size)).map(PendingRowResponse::from);
	}
}
