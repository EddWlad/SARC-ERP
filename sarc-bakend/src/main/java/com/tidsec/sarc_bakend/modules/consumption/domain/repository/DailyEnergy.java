package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sesiones y energia conocida de un dia local (por fecha de inicio); ver {@link EnergyTotals}. */
public record DailyEnergy(LocalDate date, long sessions, BigDecimal energyKwh, long sessionsWithoutEnergy) {

	public static DailyEnergy of(LocalDate date, long sessions, BigDecimal sum, long readings) {
		return new DailyEnergy(date, sessions, EnergyTotals.knownEnergy(sessions, sum, readings), sessions - readings);
	}
}
