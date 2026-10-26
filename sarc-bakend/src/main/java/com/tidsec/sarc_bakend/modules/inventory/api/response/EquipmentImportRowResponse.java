package com.tidsec.sarc_bakend.modules.inventory.api.response;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentImportRow;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowClassification;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentRowOutcome;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Fila de la vista previa: celdas tal como llegaron, clasificacion, observaciones, decision y
 * resultado. {@code equipmentId} es el equipo existente con esa serie, si lo hay.
 */
public record EquipmentImportRowResponse(
		UUID id,
		int rowNumber,
		@Schema(nullable = true) String network,
		@Schema(nullable = true) String type,
		@Schema(nullable = true) String brand,
		@Schema(nullable = true) String name,
		@Schema(nullable = true) String serial,
		@Schema(nullable = true) String assignedCode,
		@Schema(nullable = true) String uidPuk,
		@Schema(nullable = true) String location,
		@Schema(nullable = true) String mapsUrl,
		@Schema(nullable = true) String ocppUrl,
		@Schema(nullable = true) String usageStatus,
		List<String> connectors,
		@Schema(nullable = true) String enerlinkScope,
		@Schema(nullable = true) String enerlinkCharger,
		@Schema(nullable = true) String wallboxId,
		@Schema(nullable = true) String ocppId,
		EquipmentRowClassification classification,
		List<RowIssue> issues,
		@Schema(nullable = true) UUID equipmentId,
		boolean requiresApproval,
		boolean approved,
		@Schema(nullable = true) EquipmentRowOutcome outcome,
		@Schema(nullable = true) String outcomeDetail) {

	public static EquipmentImportRowResponse from(EquipmentImportRow row) {
		EquipmentLine line = row.getLine();
		List<String> connectors = Stream.of(line.connector1(), line.connector2(), line.connector3())
				.filter(Objects::nonNull).toList();
		return new EquipmentImportRowResponse(row.getId(), row.getRowNumber(), line.network(), line.infrastructure(),
				line.brand(), line.name(), line.serial(), line.assignedCode(), line.uidPuk(), line.location(),
				line.mapsUrl(), line.ocppUrl(), line.usageStatus(), connectors, line.enerlinkScope(),
				line.enerlinkCharger(), line.wallboxId(), line.ocppId(), row.getClassification(), row.getIssues(),
				row.getEquipmentId(), row.requiresApproval(), row.isApproved(), row.getOutcome(), row.getOutcomeDetail());
	}
}
