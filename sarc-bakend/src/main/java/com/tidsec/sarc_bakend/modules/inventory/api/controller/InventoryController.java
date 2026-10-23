package com.tidsec.sarc_bakend.modules.inventory.api.controller;

import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.inventory.api.response.InventorySummaryResponse;
import com.tidsec.sarc_bakend.modules.inventory.application.service.EquipmentQueryService;
import com.tidsec.sarc_bakend.modules.inventory.application.service.InventoryReportService;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.ChargingNetwork;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.InfrastructureType;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.report.ReportFile;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Tablero y reportes del inventario; aceptan los mismos filtros que el listado. */
@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController {

	private final EquipmentQueryService queries;
	private final InventoryReportService reports;

	InventoryController(EquipmentQueryService queries, InventoryReportService reports) {
		this.queries = queries;
		this.reports = reports;
	}

	@GetMapping("/summary")
	InventorySummaryResponse summary(
			@RequestParam(required = false) @Size(max = 100) String text,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) EquipmentBrand brand,
			@RequestParam(required = false) AdministrativeStatus status,
			@RequestParam(required = false) UsageStatus usageStatus) {
		return InventorySummaryResponse.from(
				queries.summary(new EquipmentSearchCriteria(text, network, type, brand, status, usageStatus)));
	}

	/** Descarga el reporte en PDF (corporativo) o XLSX (detalle verificable). */
	@GetMapping("/report")
	ResponseEntity<byte[]> report(
			@RequestParam ReportFormat format,
			@RequestParam(required = false) @Size(max = 100) String text,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) EquipmentBrand brand,
			@RequestParam(required = false) AdministrativeStatus status,
			@RequestParam(required = false) UsageStatus usageStatus) {
		ReportFile file = reports.generate(new EquipmentSearchCriteria(text, network, type, brand, status, usageStatus),
				format);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.format().contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.filename()).build().toString())
				.body(file.content());
	}
}
