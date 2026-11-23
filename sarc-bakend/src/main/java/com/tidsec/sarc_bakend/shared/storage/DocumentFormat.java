package com.tidsec.sarc_bakend.shared.storage;

import java.util.Arrays;

/**
 * Documentos aceptados como evidencia ademas de las imagenes (feature 010): PDF. Se reconoce por su firma
 * binaria {@code %PDF-}, no por el nombre ni por el Content-Type del cliente (TRD 16.1).
 */
public enum DocumentFormat {

	PDF('%', 'P', 'D', 'F', '-');

	private final byte[] signature;

	DocumentFormat(int... values) {
		this.signature = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			this.signature[i] = (byte) values[i];
		}
	}

	/** Verdadero si el contenido empieza con la firma de un PDF. */
	public static boolean isPdf(byte[] content) {
		return content != null && PDF.matches(content);
	}

	private boolean matches(byte[] content) {
		return content.length >= signature.length
				&& Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
	}
}
