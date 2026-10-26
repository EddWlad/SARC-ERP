package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.ImportRow;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowClassification;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.batch.RowOutcome;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;

import io.swagger.v3.oas.annotations.media.Schema;

/** Fila de la vista previa con su clasificacion, observaciones y decisiones. */
public record ImportRowResponse(
		UUID id,
		int rowNumber,
		@Schema(nullable = true) String externalSessionId,
		@Schema(nullable = true) Instant startedAt,
		@Schema(nullable = true) Instant endedAt,
		@Schema(nullable = true) BigDecimal energyKwh,
		@Schema(nullable = true) String site,
		@Schema(nullable = true) String charger,
		@Schema(nullable = true) String serial,
		@Schema(nullable = true) String connector,
		@Schema(nullable = true) UUID equipmentId,
		@Schema(nullable = true) ChargingNetwork network,
		RowClassification classification,
		List<Issue> issues,
		boolean nonPreferredSource,
		boolean requiresApproval,
		boolean test,
		boolean approved,
		@Schema(nullable = true) RowOutcome outcome) {

	public record Issue(String code, String message) {

		static Issue from(RowIssue issue) {
			return new Issue(issue.code(), issue.message());
		}
	}

	public static ImportRowResponse from(ImportRow row) {
		return new ImportRowResponse(row.getId(), row.getRowNumber(), row.getExternalSessionId(), row.getStartedAt(),
				row.getEndedAt(), row.getEnergyKwh(), row.getSourceSite(), row.getSourceCharger(), row.getSourceSerial(),
				row.getSourceConnector(), row.getEquipmentId(), row.getNetwork(), row.getClassification(),
				row.getIssues().stream().map(Issue::from).toList(), row.isNonPreferredSource(), row.requiresApproval(),
				row.isTest(), row.isApproved(), row.getOutcome());
	}
}
