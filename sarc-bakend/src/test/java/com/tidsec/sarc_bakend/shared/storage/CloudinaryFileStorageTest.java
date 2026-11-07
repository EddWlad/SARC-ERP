package com.tidsec.sarc_bakend.shared.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.io.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
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
						new StorageProperties("qa004", "dummy-key", "dummy-secret", "sandbox", false),
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
}
