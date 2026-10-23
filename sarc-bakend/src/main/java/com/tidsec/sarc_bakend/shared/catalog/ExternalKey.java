package com.tidsec.sarc_bakend.shared.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Como identifica una plataforma externa a un equipo: ambito (cuenta, sitio) e identificador. */
public record ExternalKey(String scope, String externalId) {

	public ExternalKey {
		scope = scope == null ? "" : scope.strip();
		externalId = externalId == null ? "" : externalId.strip();
	}

	/**
	 * Forma de comparar claves escritas a mano en las plataformas ("ESTACION 4", "Estación 4"): sin
	 * distinguir mayusculas, tildes ni espacios repetidos.
	 */
	public String comparable() {
		return fold(scope) + "\u0000" + fold(externalId);
	}

	private static String fold(String text) {
		String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}
}
