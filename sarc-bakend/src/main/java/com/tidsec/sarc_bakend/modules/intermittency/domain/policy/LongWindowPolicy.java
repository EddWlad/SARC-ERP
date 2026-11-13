package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.ReviewReason;

/** Una ventana cerrada de 240 minutos o mas queda por revisar antes de contar como oficial (FR-021a). */
public final class LongWindowPolicy {

	public static final int THRESHOLD_MINUTES = 240;

	private LongWindowPolicy() {
	}

	public static boolean isLong(long minutes) {
		return minutes >= THRESHOLD_MINUTES;
	}

	/** Motivos de revision de una ventana cerrada de esa duracion. */
	public static List<ReviewReason> reasonsFor(long minutes) {
		return isLong(minutes) ? List.of(ReviewReason.LONG_WINDOW) : List.of();
	}
}
