package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.command.RegisterMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Los minutos se aceptan decimales: un valor no entero se conserva como observacion (FR-007). */
public record RegisterMeasurementRequest(@NotNull UUID connectorId, @NotNull MeasurementSource source,
		@NotNull OffsetDateTime periodStart, @NotNull OffsetDateTime periodEnd, @NotNull PeriodType periodType,
		boolean exactConfirmed, @NotNull BigDecimal availableMinutes, @NotNull BigDecimal occupiedMinutes,
		@NotNull BigDecimal faultMinutes, @NotNull BigDecimal offlineMinutes, BigDecimal reportedPercent,
		@NotNull MeasurementOrigin origin, @Size(max = 4000) String sourceText) {

	public RegisterMeasurementCommand toCommand() {
		return new RegisterMeasurementCommand(connectorId, source, periodStart.toInstant(), periodEnd.toInstant(),
				periodType, exactConfirmed, availableMinutes, occupiedMinutes, faultMinutes, offlineMinutes,
				reportedPercent, origin, sourceText);
	}
}
