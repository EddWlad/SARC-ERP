package com.tidsec.sarc_bakend.shared.audit;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Huella SHA-256 de un archivo entregado o recibido (reportes, originales de importacion). Se
 * audita para poder demostrar despues que contenido exacto se emitio o se cargo.
 */
public final class ContentFingerprint {

	private ContentFingerprint() {
	}

	public static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}
}
