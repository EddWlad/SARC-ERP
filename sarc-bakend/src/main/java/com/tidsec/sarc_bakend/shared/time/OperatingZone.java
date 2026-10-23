package com.tidsec.sarc_bakend.shared.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Zona horaria de operacion (TRD 03.3: se guarda en UTC y se opera en America/Guayaquil). Define el
 * dia al que pertenece un instante y donde empieza cada dia para filtrar por periodo.
 */
public record OperatingZone(ZoneId zone) {

	public LocalDate dateOf(Instant instant) {
		return instant.atZone(zone).toLocalDate();
	}

	/** Primer instante del dia en esta zona; con `to` exclusivo forma rangos [desde, hasta). */
	public Instant startOf(LocalDate date) {
		return date.atStartOfDay(zone).toInstant();
	}
}
