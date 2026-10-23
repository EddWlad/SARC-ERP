package com.tidsec.sarc_bakend.modules.identity.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** Correo normalizado (sin espacios, en minusculas). La unicidad se evalua sobre esta forma. */
public record Email(String value) {

	private static final int MAX_LENGTH = 254;
	private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	public Email {
		value = normalize(value);
		if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
			throw new BusinessRuleException("INVALID_EMAIL", "El correo electronico no es valido");
		}
	}

	/** Normalizacion tolerante, tambien usada al buscar credenciales sin validar el formato. */
	public static String normalize(String raw) {
		return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
	}
}
