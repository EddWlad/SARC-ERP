package com.tidsec.sarc_bakend.modules.uptime.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** {@code exactConfirmed} es opcional y solo surte efecto en filas alineadas y no observadas. */
public record ConfirmUptimeImportRequest(@NotNull Long version, @Schema(nullable = true) Boolean exactConfirmed) {

	public boolean wantsExact() {
		return Boolean.TRUE.equals(exactConfirmed);
	}
}
