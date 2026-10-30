package com.tidsec.sarc_bakend.modules.consumption.domain.repository;

import java.math.BigDecimal;

/**
 * Agregado de un conjunto de sesiones: cantidad, energia conocida, equipos fisicos distintos y
 * sesiones sin energia informada. {@code energyKwh} es nula si hay sesiones y ninguna lectura
 * ("Sin datos"); sin sesiones vale cero.
 */
public record EnergyTotals(long sessions, BigDecimal energyKwh, long equipment, long sessionsWithoutEnergy) {

	public static final EnergyTotals EMPTY = new EnergyTotals(0, BigDecimal.ZERO, 0, 0);

	/** {@code sum}: suma de las lecturas conocidas; {@code readings}: cuantas hay. */
	public static EnergyTotals of(long sessions, BigDecimal sum, long readings, long equipment) {
		return new EnergyTotals(sessions, knownEnergy(sessions, sum, readings), equipment, sessions - readings);
	}

	static BigDecimal knownEnergy(long sessions, BigDecimal sum, long readings) {
		if (sessions == 0) {
			return BigDecimal.ZERO;
		}
		return readings == 0 ? null : sum;
	}

	public long readings() {
		return sessions - sessionsWithoutEnergy;
	}
}
