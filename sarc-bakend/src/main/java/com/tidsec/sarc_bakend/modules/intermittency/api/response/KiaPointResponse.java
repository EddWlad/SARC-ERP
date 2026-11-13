package com.tidsec.sarc_bakend.modules.intermittency.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** Punto de carga KIA para elegirlo al registrar o filtrar. El codigo es nulo en un punto LIBRE sin codigo. */
public record KiaPointResponse(UUID id, String name, @Schema(nullable = true) String assignedCode,
		@Schema(nullable = true) String usageStatus, AdministrativeStatus adminStatus) {

	public static KiaPointResponse from(KiaPointReference point) {
		return new KiaPointResponse(point.id(), point.name(), point.assignedCode(), point.usageStatus(),
				point.adminStatus());
	}
}
