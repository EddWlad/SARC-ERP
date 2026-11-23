package com.tidsec.sarc_bakend.modules.incident.domain.model.numbering;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codigo de un ticket: {@code TICKET-} seguido del numero consecutivo con al menos tres digitos y ceros a la
 * izquierda (FR-012): {@code TICKET-086}, {@code TICKET-999}, {@code TICKET-1000}. Un numero de mas de tres digitos
 * nunca se recorta. La columna {@code ticket_code} de V021 genera el mismo formato.
 */
public record TicketCode(int number) {

	private static final String PREFIX = "TICKET-";
	private static final int MIN_DIGITS = 3;
	private static final Pattern FORMAT = Pattern.compile("^TICKET-(\\d{3,9})$");

	public TicketCode {
		if (number < 1) {
			throw new IllegalArgumentException("El numero de ticket debe ser mayor o igual que 1");
		}
	}

	/** El texto del codigo, por ejemplo {@code TICKET-086}. */
	public String text() {
		String digits = Integer.toString(number);
		return PREFIX + "0".repeat(Math.max(0, MIN_DIGITS - digits.length())) + digits;
	}

	/** Interpreta un codigo escrito, sin distinguir mayusculas; vacio si no tiene el formato. */
	public static Optional<TicketCode> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher matcher = FORMAT.matcher(text.strip().toUpperCase(java.util.Locale.ROOT));
		if (!matcher.matches()) {
			return Optional.empty();
		}
		int number = Integer.parseInt(matcher.group(1));
		return number < 1 ? Optional.empty() : Optional.of(new TicketCode(number));
	}

	@Override
	public String toString() {
		return text();
	}
}
