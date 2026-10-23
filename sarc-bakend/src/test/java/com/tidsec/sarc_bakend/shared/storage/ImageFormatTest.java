package com.tidsec.sarc_bakend.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageFormatTest {

	@Test
	void detectsFormatsBySignatureNotByName() {
		assertThat(ImageFormat.detect(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 }))
				.contains(ImageFormat.JPEG);
		assertThat(ImageFormat.detect(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0 }))
				.contains(ImageFormat.PNG);
		assertThat(ImageFormat.detect("RIFF0000WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)))
				.contains(ImageFormat.WEBP);
	}

	@Test
	void rejectsAnythingElse() {
		assertThat(ImageFormat.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(ImageFormat.detect(new byte[0])).isEmpty();
	}
}
