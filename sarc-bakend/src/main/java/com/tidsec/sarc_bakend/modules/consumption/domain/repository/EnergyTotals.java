package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;

/** Agregado de un conjunto de sesiones: cantidad, energia y equipos fisicos distintos. */
public record EnergyTotals(long sessions, BigDecimal energyKwh, long equipment) {

	public static final EnergyTotals EMPTY = new EnergyTotals(0, BigDecimal.ZERO, 0);
}
