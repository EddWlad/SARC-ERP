package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

import java.time.Duration;

/** Tipo de periodo. Solo {@code EXACT_WEEK} es oficial; las ventanas moviles son provisionales. */
public enum PeriodType {

	ROLLING_24H(Duration.ofHours(24)),
	ROLLING_7D(Duration.ofDays(7)),
	ROLLING_30D(Duration.ofDays(30)),
	EXACT_WEEK(Duration.ofDays(7));

	private final Duration nominal;

	PeriodType(Duration nominal) {
		this.nominal = nominal;
	}

	public Duration nominal() {
		return nominal;
	}

	public boolean isExact() {
		return this == EXACT_WEEK;
	}
}
