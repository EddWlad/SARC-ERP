package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

/** Hallazgo que deja la medicion observada. Las no validables exigen corregir el dato antes. */
public enum Observation {

	SUM_BELOW_DURATION(true),
	PERCENT_MISMATCH(true),
	SUM_ABOVE_DURATION(false),
	SUM_ZERO(false),
	PERCENT_OUT_OF_RANGE(false),
	DURATION_INVALID(false),
	WINDOW_DURATION_MISMATCH(false);

	private final boolean validatable;

	Observation(boolean validatable) {
		this.validatable = validatable;
	}

	public boolean validatable() {
		return validatable;
	}
}
