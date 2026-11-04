package com.tidsec.sarc_bakend.modules.uptime.api.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tidsec.sarc_bakend.modules.uptime.api.request.BatchValidationRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.ClassifyRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.CorrectMeasurementRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.JustifiedVersionRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.ParseBlockRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.RegisterMeasurementRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.request.ValidateMeasurementRequest;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ClassificationResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.GridRowResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.MeasurementDetailResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.MeasurementHistoryResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.MeasurementResponse;
import com.tidsec.sarc_bakend.modules.uptime.api.response.ParsedBlockResponse;
import com.tidsec.sarc_bakend.modules.uptime.application.result.BatchValidationResult;
import com.tidsec.sarc_bakend.modules.uptime.application.result.RegistrationResult;
import com.tidsec.sarc_bakend.modules.uptime.application.service.MeasurementGridService;
import com.tidsec.sarc_bakend.modules.uptime.application.service.MeasurementReadService;
import com.tidsec.sarc_bakend.modules.uptime.application.service.MeasurementService;
import com.tidsec.sarc_bakend.modules.uptime.application.service.MeasurementValidationService;
import com.tidsec.sarc_bakend.modules.uptime.domain.repository.MeasurementCriteria;
import com.tidsec.sarc_bakend.shared.catalog.ConnectorQuery;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.pagination.PageQuery;
import com.tidsec.sarc_bakend.shared.pagination.PageResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Captura de mediciones de uptime por conector (RF-004). */
@RestController
@RequestMapping("/api/v1/uptime")
class UptimeMeasurementController {

	private final MeasurementService measurements;
	private final MeasurementGridService grid;
	private final MeasurementValidationService validation;
	private final MeasurementReadService reads;

	UptimeMeasurementController(MeasurementService measurements, MeasurementGridService grid,
			MeasurementValidationService validation, MeasurementReadService reads) {
		this.measurements = measurements;
		this.grid = grid;
		this.validation = validation;
		this.reads = reads;
	}

	/** Sin estado y sin escribir: la persona revisa los valores antes de guardar. */
	@PostMapping("/measurements/parse")
	ParsedBlockResponse parse(@Valid @RequestBody ParseBlockRequest request) {
		return ParsedBlockResponse.from(measurements.parseBlock(request.text()));
	}

	/** 201 creada; 200 identica a la existente, sin escribir. */
	@PostMapping("/measurements")
	ResponseEntity<MeasurementResponse> register(@Valid @RequestBody RegisterMeasurementRequest request) {
		RegistrationResult result = measurements.register(request.toCommand());
		HttpStatus status = result.outcome() == RegistrationResult.Outcome.CREATED ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(MeasurementResponse.from(result.view(), result.outcome().name()));
	}

	@PostMapping("/measurements/{id}/corrections")
	MeasurementResponse correct(@PathVariable UUID id, @Valid @RequestBody CorrectMeasurementRequest request) {
		return MeasurementResponse.from(measurements.correct(id, request.toCommand()), "CORRECTED");
	}

	@PostMapping("/measurements/classifications")
	List<ClassificationResponse> classify(@Valid @RequestBody ClassifyRequest request) {
		return measurements.classify(request.toCommand()).stream().map(ClassificationResponse::from).toList();
	}

	@PostMapping("/measurements/{id}/validate")
	MeasurementResponse validate(@PathVariable UUID id, @Valid @RequestBody ValidateMeasurementRequest request) {
		return MeasurementResponse.from(validation.validate(id, request.version(), request.justification()), "VALIDATED");
	}

	@PostMapping("/measurements/validation")
	BatchValidationResult validateBatch(@Valid @RequestBody BatchValidationRequest request) {
		return validation.validateBatch(request.items().stream()
				.map(item -> new MeasurementValidationService.Item(item.id(), item.version())).toList());
	}

	@PostMapping("/measurements/{id}/exclude")
	MeasurementResponse exclude(@PathVariable UUID id, @Valid @RequestBody JustifiedVersionRequest request) {
		return MeasurementResponse.from(validation.exclude(id, request.version(), request.justification()), "EXCLUDED");
	}

	@PostMapping("/measurements/{id}/restore")
	MeasurementResponse restore(@PathVariable UUID id, @Valid @RequestBody JustifiedVersionRequest request) {
		return MeasurementResponse.from(validation.restore(id, request.version(), request.justification()), "RESTORED");
	}

	/** Consulta recibe solo vigentes no excluidas; {@code queue} es operativo. */
	@GetMapping("/measurements")
	PageResult<MeasurementDetailResponse> search(@RequestParam(required = false) UUID connectorId,
			@RequestParam(required = false) UUID equipmentId,
			@RequestParam(required = false) MeasurementCriteria.Queue queue,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
		return reads.search(connectorId, equipmentId, queue, new PageQuery(page, size))
				.map(MeasurementDetailResponse::from);
	}

	@GetMapping("/measurements/{id}")
	MeasurementDetailResponse detail(@PathVariable UUID id) {
		return MeasurementDetailResponse.from(reads.detail(id));
	}

	@GetMapping("/measurements/{id}/revisions")
	MeasurementHistoryResponse revisions(@PathVariable UUID id) {
		return MeasurementHistoryResponse.from(reads.history(id));
	}

	/** {@code periodStart} y {@code periodEnd} son instantes exactos: la situacion es por identidad. */
	@GetMapping("/grid")
	List<GridRowResponse> grid(@RequestParam OffsetDateTime periodStart, @RequestParam OffsetDateTime periodEnd,
			@RequestParam(required = false) ChargingNetwork network,
			@RequestParam(required = false) InfrastructureType type,
			@RequestParam(required = false) UUID equipmentId,
			@RequestParam(defaultValue = "false") boolean showInactive) {
		ConnectorQuery query = new ConnectorQuery(null, network, type, null, equipmentId);
		return grid.grid(query, periodStart.toInstant(), periodEnd.toInstant(), showInactive).stream()
				.map(GridRowResponse::from).toList();
	}
}
