package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

/**
 * Franjas de duracion total de una ventana para el pastel de FR-036: [0,5), [5,15), [15,60), [60,240) y
 * [240,infinito). Un limite pertenece a la franja de arriba: 5 min es «5 a 14», 240 min es «4 h o mas».
 */
public enum DurationBand {

	UNDER_5(0), FROM_5_TO_14(5), FROM_15_TO_59(15), FROM_60_TO_239(60), FROM_240(LongWindowPolicy.THRESHOLD_MINUTES);

	private final int lowerBound;

	DurationBand(int lowerBound) {
		this.lowerBound = lowerBound;
	}

	/** Franja de una ventana segun su duracion total, aunque cruce el limite del periodo. */
	public static DurationBand of(long totalMinutes) {
		DurationBand found = UNDER_5;
		for (DurationBand band : values()) {
			if (totalMinutes >= band.lowerBound) {
				found = band;
			}
		}
		return found;
	}
}
