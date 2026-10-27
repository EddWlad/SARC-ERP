package com.tidsec.sarc_bakend.modules.consumption.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.application.command.RowDecisionCommand;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Marca filas como prueba o aprueba correcciones; un campo omitido no cambia esa decision. */
public record RowDecisionRequest(
		@NotEmpty @Size(max = 1000) List<@NotNull UUID> rowIds,
		@Schema(nullable = true) Boolean test,
		@Schema(nullable = true) Boolean approved,
		@Schema(nullable = true) Boolean reassign) {

	public RowDecisionCommand toCommand(UUID batchId) {
		return new RowDecisionCommand(batchId, rowIds, test, approved, reassign);
	}
}
