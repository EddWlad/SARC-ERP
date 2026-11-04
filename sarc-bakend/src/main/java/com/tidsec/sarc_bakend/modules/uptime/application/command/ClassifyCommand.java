package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.time.Instant;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.MeasurementSource;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.ReadingClass;

/** Marca especial de un conector o, sin {@code connectorId}, de los conectores del equipo en alcance. */
public record ClassifyCommand(UUID equipmentId, UUID connectorId, ReadingClass readingClass, MeasurementSource source,
		Instant periodStart, Instant periodEnd, PeriodType periodType, boolean exactConfirmed, String justification) {
}
