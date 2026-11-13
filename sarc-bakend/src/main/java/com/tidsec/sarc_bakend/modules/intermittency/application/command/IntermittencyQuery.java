package com.tidsec.sarc_bakend.modules.intermittency.application.command;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Preset;

/**
 * Lo que pide quien consulta el dashboard: periodo y puntos (FR-031, FR-032). Todo es opcional; sin nada es la
 * quincena en curso de todos los puntos. {@code to} es exclusivo. El servicio lo normaliza con
 * {@code PeriodPresets} y el reloj de la operacion.
 */
public record IntermittencyQuery(Preset preset, YearMonth month, LocalDate from, LocalDate to,
		Collection<UUID> equipmentIds) {

	public IntermittencyQuery {
		equipmentIds = equipmentIds == null ? Set.of() : Set.copyOf(equipmentIds);
	}
}
