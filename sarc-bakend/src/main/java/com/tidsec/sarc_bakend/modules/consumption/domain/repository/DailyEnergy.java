package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Sesiones y energia de un dia local (por fecha de inicio). */
public record DailyEnergy(LocalDate date, long sessions, BigDecimal energyKwh) {
}
