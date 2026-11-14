package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import java.time.ZoneOffset;

/** Conversion entre el desplazamiento de origen y los minutos que se guardan en la base. */
public final class SourceOffsets {

	private SourceOffsets() {
	}

	public static Integer toMinutes(ZoneOffset offset) {
		return offset == null ? null : offset.getTotalSeconds() / 60;
	}

	public static ZoneOffset toOffset(Integer minutes) {
		return minutes == null ? null : ZoneOffset.ofTotalSeconds(minutes * 60);
	}
}
