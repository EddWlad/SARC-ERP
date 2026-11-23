package com.tidsec.sarc_bakend.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DocumentFormatTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };

	@Test
	void recognisesAPdfByItsBinarySignature() {
		assertThat(DocumentFormat.isPdf("%PDF-1.7\n%...".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
		assertThat(DocumentFormat.isPdf("%PDF-".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
	}

	@Test
	void aPngRenamedAsPdfIsDetectedAsAnImageAndNotAsPdf() {
		assertThat(DocumentFormat.isPdf(PNG)).isFalse();
		assertThat(ImageFormat.detect(PNG)).contains(ImageFormat.PNG);
	}

	@Test
	void rejectsARenamedExecutableAndTruncatedOrEmptyContent() {
		assertThat(DocumentFormat.isPdf(new byte[] { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0 })).isFalse();
		assertThat(DocumentFormat.isPdf("%PDF".getBytes(StandardCharsets.ISO_8859_1))).isFalse();
		assertThat(DocumentFormat.isPdf(new byte[0])).isFalse();
		assertThat(DocumentFormat.isPdf(null)).isFalse();
		assertThat(ImageFormat.detect(new byte[] { 'M', 'Z', (byte) 0x90, 0 })).isEmpty();
	}

	@Test
	void theSignatureMustBeAtTheStartOfTheFile() {
		assertThat(DocumentFormat.isPdf(" %PDF-1.4".getBytes(StandardCharsets.ISO_8859_1))).isFalse();
	}
}
