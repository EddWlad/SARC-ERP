package com.tidsec.sarc_bakend.modules.uptime.application.port;

import java.math.BigDecimal;

/** Interpreta el bloque de texto de un conector, sin guardar nada. */
public interface MeasurementBlockParser {

	/** Valores tal como vinieron; {@code reportedPercent} es nulo si el bloque no lo trae. */
	record Block(BigDecimal available, BigDecimal occupied, BigDecimal fault, BigDecimal offline,
			BigDecimal reportedPercent) {
	}

	Block parse(String text);
}
