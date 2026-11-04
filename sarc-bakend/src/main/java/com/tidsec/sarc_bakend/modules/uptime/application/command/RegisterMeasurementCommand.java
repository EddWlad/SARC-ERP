package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementOrigin;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;

/** Lectura digitada o interpretada de un bloque. Los minutos llegan como numero, sin redondear. */
public record RegisterMeasurementCommand(UUID connectorId, MeasurementSource source, Instant periodStart,
		Instant periodEnd, PeriodType periodType, boolean exactConfirmed, BigDecimal availableMinutes,
		BigDecimal occupiedMinutes, BigDecimal faultMinutes, BigDecimal offlineMinutes, BigDecimal reportedPercent,
		MeasurementOrigin origin, String sourceText) {
}
