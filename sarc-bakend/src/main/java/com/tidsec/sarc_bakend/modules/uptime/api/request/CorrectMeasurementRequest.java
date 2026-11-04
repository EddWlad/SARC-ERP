package com.tidsec.sarc_bakend.modules.uptime.api.request;

import java.math.BigDecimal;

import com.tidsec.sarc_bakend.modules.uptime.application.command.CorrectMeasurementCommand;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sin {@code readingClass} es una lectura medida. */
public record CorrectMeasurementRequest(@NotNull Long version, ReadingClass readingClass,
		@NotNull PeriodType periodType, boolean exactConfirmed, BigDecimal availableMinutes,
		BigDecimal occupiedMinutes, BigDecimal faultMinutes, BigDecimal offlineMinutes, BigDecimal reportedPercent,
		@NotBlank @Size(max = 500) String justification) {

	public CorrectMeasurementCommand toCommand() {
		return new CorrectMeasurementCommand(version, readingClass == null ? ReadingClass.MEASURED : readingClass,
				periodType, exactConfirmed, availableMinutes, occupiedMinutes, faultMinutes, offlineMinutes,
				reportedPercent, justification.strip());
	}
}
