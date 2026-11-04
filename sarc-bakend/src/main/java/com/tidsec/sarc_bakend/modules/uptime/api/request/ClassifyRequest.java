package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.command.ClassifyCommand;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClassifyRequest(@NotNull UUID equipmentId, UUID connectorId, @NotNull ReadingClass readingClass,
		@NotNull MeasurementSource source, @NotNull OffsetDateTime periodStart, @NotNull OffsetDateTime periodEnd,
		@NotNull PeriodType periodType, boolean exactConfirmed, @NotBlank @Size(max = 500) String justification) {

	public ClassifyCommand toCommand() {
		return new ClassifyCommand(equipmentId, connectorId, readingClass, source, periodStart.toInstant(),
				periodEnd.toInstant(), periodType, exactConfirmed, justification.strip());
	}
}
