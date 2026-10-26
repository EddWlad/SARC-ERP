package com.tidsec.sarc_bakend.shared.importing.reading;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Fechas y horas de archivos de origen. Cada fuente declara sus formatos y la zona en la que estan
 * escritas; el resultado es un instante UTC (TRD 03.3). Los formatos se prueban en orden y no se
 * admiten fechas imposibles (31/02).
 */
public final class DateTimeText {

	/** Formato al que {@link TabularReader} convierte las fechas guardadas como fecha de Excel. */
	public static final DateTimeFormatter EXCEL = pattern("uuuu-MM-dd HH:mm:ss");

	private DateTimeText() {
	}

	public static DateTimeFormatter pattern(String pattern) {
		return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
	}

	public static Instant parse(String raw, List<DateTimeFormatter> formats, ZoneId zone) {
		String text = raw == null ? "" : raw.strip();
		if (text.isEmpty()) {
			throw new InvalidValueException("DATE_REQUIRED", "Falta la fecha");
		}
		for (DateTimeFormatter format : formats) {
			try {
				return LocalDateTime.parse(text, format).atZone(zone).toInstant();
			}
			catch (DateTimeParseException ignored) {
				// Se prueba el siguiente formato admitido.
			}
		}
		throw new InvalidValueException("INVALID_DATE", "\"" + raw + "\" no es una fecha y hora valida");
	}
}
