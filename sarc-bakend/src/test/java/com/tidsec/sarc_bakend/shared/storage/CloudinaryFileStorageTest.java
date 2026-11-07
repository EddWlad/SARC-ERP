package com.tidsec.sarc_bakend.shared.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.*;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;

class CloudinaryFileStorageTest {
	@SuppressWarnings("unchecked")
	@Test
	void privateReaderReturnsBytesAndClosesStreamWithoutRedirectingClient() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<InputStream> response = mock(HttpResponse.class);
		var body = spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
		when(response.statusCode()).thenReturn(200);
		when(response.body()).thenReturn(body);
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(response);
		var storage =
				new CloudinaryFileStorage(
						new StorageProperties("qa004", "dummy-key", "dummy-secret", "sandbox", false, false),
						client);
		assertThat(storage.readDocument("qa004/private")).containsExactly(1, 2, 3);
		verify(body).close();
		var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
		verify(client).send(capture.capture(), any(HttpResponse.BodyHandler.class));
		assertThat(capture.getValue().uri().getScheme()).isEqualTo("https");
		when(response.statusCode()).thenReturn(403);
		assertThatThrownBy(() -> storage.readDocument("qa004/private"))
				.isInstanceOf(ServiceUnavailableException.class)
				.hasMessageNotContaining("dummy-secret")
				.hasMessageNotContaining("https:");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void requestsBackupPerUploadOnlyWhenEnabled(boolean backup) throws Exception {
		Uploader uploader = mock(Uploader.class);
		byte[] content = {1, 2, 3};
		when(uploader.upload(same(content), anyMap())).thenReturn(Map.of("public_id", "sarc-qa/sample"));
		try (var ignored = mockConstruction(Cloudinary.class,
				(cloudinary, context) -> when(cloudinary.uploader()).thenReturn(uploader))) {
			var properties = new StorageProperties("qa004", "dummy-key", "dummy-secret", "sarc-qa", true, backup);
			var storage = new CloudinaryFileStorage(properties);
			assertThat(storage.store("images", content)).isEqualTo("sarc-qa/sample");
			assertThat(storage.storeDocument("reports", "sample.pdf", content)).isEqualTo("sarc-qa/sample");
			verify(uploader).upload(same(content), argThat(options ->
					"image".equals(options.get("resource_type")) && "sarc-qa/images".equals(options.get("folder"))
							&& (backup ? Boolean.TRUE.equals(options.get("backup")) : !options.containsKey("backup"))));
			verify(uploader).upload(same(content), argThat(options ->
					"raw".equals(options.get("resource_type")) && "sarc-qa/reports".equals(options.get("folder"))
							&& "sample.pdf".equals(options.get("filename_override"))
							&& (backup ? Boolean.TRUE.equals(options.get("backup")) : !options.containsKey("backup"))));
		}
	}
}
