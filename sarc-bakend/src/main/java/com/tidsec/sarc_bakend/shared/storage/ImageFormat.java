package com.tidsec.sarc_bakend.shared.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Formatos de imagen aceptados. Se reconocen por su firma binaria, no por el nombre ni por el
 * Content-Type que envia el cliente (TRD 16.1: comprobar MIME real).
 */
public enum ImageFormat {

	JPEG(new Marker(0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)),
	PNG(new Marker(0, (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)),
	/** Contenedor RIFF (bytes 0-3) con tipo WEBP (bytes 8-11). */
	WEBP(new Marker(0, 'R', 'I', 'F', 'F'), new Marker(8, 'W', 'E', 'B', 'P'));

	private final List<Marker> markers;

	ImageFormat(Marker... markers) {
		this.markers = List.of(markers);
	}

	public static Optional<ImageFormat> detect(byte[] content) {
		return Arrays.stream(values()).filter(format -> format.matches(content)).findFirst();
	}

	private boolean matches(byte[] content) {
		return markers.stream().allMatch(marker -> marker.foundIn(content));
	}

	/** Bytes esperados a partir de una posicion fija del archivo. */
	private record Marker(int offset, byte[] bytes) {

		Marker(int offset, int... values) {
			this(offset, toBytes(values));
		}

		boolean foundIn(byte[] content) {
			return content.length >= offset + bytes.length
					&& Arrays.equals(content, offset, offset + bytes.length, bytes, 0, bytes.length);
		}

		private static byte[] toBytes(int[] values) {
			byte[] bytes = new byte[values.length];
			for (int i = 0; i < values.length; i++) {
				bytes[i] = (byte) values[i];
			}
			return bytes;
		}
	}
}
