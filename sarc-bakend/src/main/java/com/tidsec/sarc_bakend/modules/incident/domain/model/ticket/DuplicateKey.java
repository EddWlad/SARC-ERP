package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;

/**
 * Clave del aviso de duplicado (FR-016, research R4): dos tickets abiertos con la misma clave avisan uno del otro. No es
 * unica: el analista puede crear otro con un motivo.
 *
 * <ul>
 * <li>Equipo: {@code EQ:{equipmentId}}, tambien para un ticket de conectores, de modo que avisa de otro abierto del
 * mismo equipo, completo o con otros conectores.
 * <li>Chatbot: {@code CHATBOT}.
 * <li>Elemento del sitio: {@code SITE:{clase}:{sitio}:{elemento}}. El sitio es el equipo de referencia o el nombre
 * escrito a mano, normalizado (minusculas, sin tildes ni espacios repetidos); el elemento es su nombre normalizado o
 * vacio. Una barrera con otro nombre no avisa.
 * </ul>
 */
public final class DuplicateKey {

	/** Largo de la columna {@code duplicate_key}. */
	static final int MAX_LENGTH = 200;

	private DuplicateKey() {
	}

	public static String of(AffectedElement element) {
		return switch (element.elementClass()) {
			case EQUIPMENT -> forEquipment(element.equipmentId());
			case CHATBOT_SERVICE -> forChatbot();
			case SITE_ELEMENT -> forSite(element.siteElementKind(), element.referenceEquipmentId(),
					element.manualSiteName(), element.elementName());
		};
	}

	public static String forEquipment(UUID equipmentId) {
		return "EQ:" + equipmentId;
	}

	public static String forChatbot() {
		return "CHATBOT";
	}

	/** {@code referenceEquipmentId} identifica el sitio si existe; si no, lo identifica el nombre escrito a mano. */
	public static String forSite(SiteElementKind kind, UUID referenceEquipmentId, String manualSiteName,
			String elementName) {
		String place = referenceEquipmentId != null ? referenceEquipmentId.toString() : normalize(manualSiteName);
		String name = normalize(elementName);
		String key = "SITE:" + kind + ":" + place + ":" + name;
		// Un nombre largo no cabe en la columna: se resume con su huella, sin perder que dos iguales coinciden.
		return key.length() <= MAX_LENGTH ? key : "SITE:" + kind + ":#" + sha256(place + "|" + name);
	}

	/** Minusculas, sin tildes y con los espacios repetidos reducidos a uno. */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
