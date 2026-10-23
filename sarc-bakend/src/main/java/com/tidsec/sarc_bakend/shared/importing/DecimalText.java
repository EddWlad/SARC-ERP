package com.tidsec.sarc_bakend.shared.importing;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Convierte cantidades escritas con coma o punto decimal (PRD RF-003.4, CA-003-02: "35,41" son
 * 35.41 kWh, nunca 3541). Cada fuente declara su separador decimal; un valor con el otro separador
 * se acepta solo si no hay duda. Lo dudoso (p. ej. "1.234" en una fuente con coma decimal) se
 * rechaza en vez de adivinar.
 */
public final class DecimalText {

	private static final Pattern ALLOWED = Pattern.compile("-?[0-9.,]+");

	private DecimalText() {
	}

	public static BigDecimal parse(String raw, char decimalSeparator) {
		String text = raw == null ? "" : raw.replace(" ", "").replace(" ", "").strip();
		if (text.isEmpty()) {
			throw new InvalidValueException("NUMBER_REQUIRED", "Falta el valor numerico");
		}
		if (!ALLOWED.matcher(text).matches()) {
			throw new InvalidValueException("INVALID_NUMBER", "\"" + raw + "\" no es un numero");
		}
		boolean negative = text.startsWith("-");
		String digits = negative ? text.substring(1) : text;
		String normalized = normalize(raw, digits, decimalSeparator);
		BigDecimal value = new BigDecimal(normalized);
		return negative ? value.negate() : value;
	}

	private static String normalize(String raw, String digits, char declared) {
		int lastDot = digits.lastIndexOf('.');
		int lastComma = digits.lastIndexOf(',');
		if (lastDot < 0 && lastComma < 0) {
			return digits;
		}
		if (lastDot >= 0 && lastComma >= 0) {
			// Ambos separadores: el ultimo es el decimal y el otro debe agrupar miles.
			char decimal = lastDot > lastComma ? '.' : ',';
			char grouping = decimal == '.' ? ',' : '.';
			String integerPart = digits.substring(0, digits.lastIndexOf(decimal));
			String fraction = digits.substring(digits.lastIndexOf(decimal) + 1);
			if (fraction.isEmpty() || fraction.indexOf(grouping) >= 0 || !isGrouped(integerPart, grouping)) {
				throw ambiguous(raw);
			}
			return integerPart.replace(String.valueOf(grouping), "") + "." + fraction;
		}
		char separator = lastDot >= 0 ? '.' : ',';
		long occurrences = digits.chars().filter(c -> c == separator).count();
		if (occurrences > 1) {
			// Solo puede ser agrupacion de miles de un entero ("1.234.567").
			if (!isGrouped(digits, separator)) {
				throw ambiguous(raw);
			}
			return digits.replace(String.valueOf(separator), "");
		}
		int index = digits.indexOf(separator);
		String integerPart = digits.substring(0, index);
		String fraction = digits.substring(index + 1);
		if (integerPart.isEmpty() || fraction.isEmpty()) {
			throw ambiguous(raw);
		}
		if (separator != declared && fraction.length() == 3) {
			// "1.234" con coma decimal declarada: puede ser 1234 o 1,234. No se adivina.
			throw ambiguous(raw);
		}
		return integerPart + "." + fraction;
	}

	/** Entero agrupado de a tres digitos con el separador indicado ("1.234.567"). */
	private static boolean isGrouped(String digits, char grouping) {
		return Pattern.matches("[0-9]{1,3}(" + Pattern.quote(String.valueOf(grouping)) + "[0-9]{3})+", digits);
	}

	private static InvalidValueException ambiguous(String raw) {
		return new InvalidValueException("AMBIGUOUS_NUMBER",
				"\"" + raw + "\" es ambiguo: no se puede saber cual es el separador decimal");
	}
}
