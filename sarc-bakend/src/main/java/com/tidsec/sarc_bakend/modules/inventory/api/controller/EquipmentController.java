package com.tidsec.sarc_bakend.modules.inventory.api.controller;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tidsec.sarc_bakend.modules.inventory.api.request.ChangeStatusRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.CreateEquipmentRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.ExternalIdentityRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.ScheduleRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.request.UpdateEquipmentRequest;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentResponse;
import com.tidsec.sarc_bakend.modules.inventory.api.response.EquipmentSummaryResponse;
import com.tidsec.sarc_bakend.modules.inventory.api.response.ExternalIdentityResponse;
import com.tidsec.sarc_bakend.modules.inventory.application.command.ChangeEquipmentStatusCommand;
import com.tidsec.sarc_bakend.modules.inventory.application.service.EquipmentQueryService;
import com.tidsec.sarc_bakend.modules.inventory.application.service.EquipmentService;
import com.tidsec.sarc_bakend.modules.inventory.application.service.ExternalIdentityService;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

@RestController
@RequestMapping("/api/v1/equipment")
class EquipmentController {

	private final EquipmentService equipmentService;
	private final EquipmentQueryService queries;
	private final ExternalIdentityService externalIdentities;

	EquipmentController(EquipmentService equipmentService, EquipmentQueryService queries,
			ExternalIdentityService externalIdentities) {
		this.equipmentService = equipmentService;
		this.queries = queries;
		this.externalIdentities = externalIdentities;
	}

	/** Sin {@code status} no se listan los eliminados. */
	@GetMapping
	PageResult<EquipmentSummaryResponse> list(
			@RequestParam(required = false) @Size(max = 100) String text,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) EquipmentBrand brand,
			@RequestParam(required = false) AdministrativeStatus status,
			@RequestParam(required = false) UsageStatus usageStatus,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return queries.search(new EquipmentSearchCriteria(text, network, type, brand, status, usageStatus),
				new PageQuery(page, size)).map(EquipmentSummaryResponse::from);
	}

	@PostMapping
	ResponseEntity<EquipmentResponse> create(@Valid @RequestBody CreateEquipmentRequest body) {
		EquipmentResponse created = EquipmentResponse.from(equipmentService.register(body.toCommand()));
		return ResponseEntity.created(URI.create("/api/v1/equipment/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	EquipmentResponse get(@PathVariable UUID id) {
		return EquipmentResponse.from(queries.get(id));
	}

	@PutMapping("/{id}")
	EquipmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEquipmentRequest body) {
		return EquipmentResponse.from(equipmentService.update(body.toCommand(id)));
	}

	/** Activar, inactivar, eliminar (baja logica) o recuperar. */
	@PatchMapping("/{id}/status")
	EquipmentResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest body) {
		return EquipmentResponse.from(
				equipmentService.changeStatus(new ChangeEquipmentStatusCommand(id, body.version(), body.status())));
	}

	@PutMapping("/{id}/schedule")
	EquipmentResponse defineSchedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest body) {
		return EquipmentResponse.from(equipmentService.defineSchedule(body.toCommand(id)));
	}

	/** Imagen JPEG, PNG o WebP de hasta 10 MiB; se guarda privada y se entrega con URL firmada. */
	@PutMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	EquipmentResponse attachImage(@PathVariable UUID id, @RequestParam long version,
			@RequestPart("file") MultipartFile file) throws IOException {
		return EquipmentResponse.from(equipmentService.attachImage(id, version, file.getBytes()));
	}

	@DeleteMapping("/{id}/image")
	EquipmentResponse removeImage(@PathVariable UUID id, @RequestParam long version) {
		return EquipmentResponse.from(equipmentService.removeImage(id, version));
	}

	@GetMapping("/{id}/external-identities")
	List<ExternalIdentityResponse> externalIdentities(@PathVariable UUID id) {
		return externalIdentities.listForEquipment(id).stream().map(ExternalIdentityResponse::from).toList();
	}

	@PostMapping("/{id}/external-identities")
	ResponseEntity<ExternalIdentityResponse> addExternalIdentity(@PathVariable UUID id,
			@Valid @RequestBody ExternalIdentityRequest body) {
		ExternalIdentityResponse created = ExternalIdentityResponse
				.from(externalIdentities.register(body.toCommand(id)));
		return ResponseEntity.created(URI.create("/api/v1/external-identities/" + created.id())).body(created);
	}
}
