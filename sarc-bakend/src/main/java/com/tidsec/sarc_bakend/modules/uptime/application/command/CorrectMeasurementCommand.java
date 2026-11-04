package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.math.BigDecimal;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;

/** Nueva revision sobre la version leida. La justificacion es obligatoria. */
public record CorrectMeasurementCommand(long version, ReadingClass readingClass, PeriodType periodType,
		boolean exactConfirmed, BigDecimal availableMinutes, BigDecimal occupiedMinutes, BigDecimal faultMinutes,
		BigDecimal offlineMinutes, BigDecimal reportedPercent, String justification) {
}
