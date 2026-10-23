package com.tidsec.sarc_bakend.shared.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de imagen aceptados. Se reconocen por su firma binaria, no por el nombre ni por el
 * Content-Type que envia el cliente (TRD 16.1: comprobar MIME real).
 */
public enum ImageFormat {

	JPEG(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, 0),
	PNG(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }, 0),
	WEBP(new byte[] { 'W', 'E', 'B', 'P' }, 8);

	private final byte[] signature;
	private final int offset;

	ImageFormat(byte[] signature, int offset) {
		this.signature = signature;
		this.offset = offset;
	}

	public static Optional<ImageFormat> detect(byte[] content) {
		return Arrays.stream(values()).filter(format -> format.matches(content)).findFirst();
	}

	private boolean matches(byte[] content) {
		if (content.length < offset + signature.length) {
			return false;
		}
		return Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
	}
}
