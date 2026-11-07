package com.tidsec.sarc_bakend.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;

/**
 * Cloudinary con entrega "authenticated": los archivos no son publicos y solo se ven con una
 * URL firmada por el backend.
 */
class CloudinaryFileStorage implements FileStorage {

	private static final Logger log = LoggerFactory.getLogger(CloudinaryFileStorage.class);
	private static final String DELIVERY_TYPE = "authenticated";
	private static final Duration DOCUMENT_URL_TTL = Duration.ofMinutes(5);

	private static final HttpClient DOCUMENT_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER).build();
	private static final int MAX_DOCUMENT_BYTES = 100 * 1024 * 1024;
	private static final Duration DOCUMENT_READ_TIMEOUT = Duration.ofSeconds(60);

	private final Cloudinary cloudinary;
	private final String rootFolder;
	private final boolean backupUploads;
	private final HttpClient documentClient;

	CloudinaryFileStorage(StorageProperties properties) {
		this(properties, DOCUMENT_CLIENT);
	}

	CloudinaryFileStorage(StorageProperties properties, HttpClient documentClient) {
		this.documentClient = documentClient;
		this.cloudinary = new Cloudinary(ObjectUtils.asMap(
				"cloud_name", properties.cloudName(),
				"api_key", properties.apiKey(),
				"api_secret", properties.apiSecret(),
				"secure", true));
		this.rootFolder = properties.rootFolder();
		this.backupUploads = properties.backupUploads();
	}

	@Override
	public String store(String folder, byte[] content) {
		try {
			Map<?, ?> result = cloudinary.uploader().upload(content, uploadOptions(folder, "image"));
			return (String) result.get("public_id");
		}
		catch (IOException | RuntimeException ex) {
			log.error("No se pudo subir el archivo a Cloudinary", ex);
			throw unavailable();
		}
	}

	@Override
	public String storeDocument(String folder, String filename, byte[] content) {
		try {
			var options = uploadOptions(folder, "raw");
			options.put("filename_override", filename);
			options.put("use_filename", true);
			Map<?, ?> result = cloudinary.uploader().upload(content, options);
			return (String) result.get("public_id");
		}
		catch (IOException | RuntimeException ex) {
			log.error("No se pudo subir el documento a Cloudinary", ex);
			throw unavailable();
		}
	}

	private Map<String, Object> uploadOptions(String folder, String resourceType) {
		Map<String, Object> options = ObjectUtils.asMap(
				"type", DELIVERY_TYPE,
				"resource_type", resourceType,
				"folder", rootFolder + "/" + folder,
				"unique_filename", true,
				"overwrite", false);
		if (backupUploads) {
			options.put("backup", true);
		}
		return options;
	}

	@Override
	public void delete(String reference) {
		try {
			cloudinary.uploader().destroy(reference, ObjectUtils.asMap("type", DELIVERY_TYPE, "resource_type", "image"));
		}
		catch (IOException | RuntimeException ex) {
			log.error("No se pudo eliminar el archivo {} de Cloudinary", reference, ex);
			throw unavailable();
		}
	}

	@Override
	public Optional<String> accessUrl(String reference) {
		return Optional.of(cloudinary.url().type(DELIVERY_TYPE).resourceType("image").secure(true).signed(true)
				.generate(reference));
	}

	/**
	 * Firmada y con vencimiento: quien la obtiene no puede reutilizarla indefinidamente. Se entrega como
	 * adjunto, para que el navegador la descargue sin reemplazar la pagina de SARC.
	 */
	@Override
	public Optional<String> documentUrl(String reference) {
		try {
			long expiresAt = Instant.now().plus(DOCUMENT_URL_TTL).getEpochSecond();
			return Optional.of(cloudinary.privateDownload(reference, null,
					ObjectUtils.asMap("type", DELIVERY_TYPE, "resource_type", "raw", "expires_at", expiresAt,
							"attachment", true)));
		}
		catch (Exception ex) {
			log.error("No se pudo firmar la descarga del documento {}", reference, ex);
			throw unavailable();
		}
	}

	@Override
	public byte[] readDocument(String reference) {
		try {
			URI uri = URI.create(documentUrl(reference).orElseThrow(CloudinaryFileStorage::unavailable));
			HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
			HttpResponse<InputStream> response = documentClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) {
					throw unavailable();
				}
				byte[] data = readLimited(body);
				if (data.length > MAX_DOCUMENT_BYTES) {
					throw unavailable();
				}
				return data;
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw unavailable();
		}
		catch (IOException | IllegalArgumentException ex) {
			throw unavailable();
		}
	}

	/** El timeout de la peticion termina con las cabeceras: cerrar el flujo corta un cuerpo detenido. */
	private static byte[] readLimited(InputStream body) throws IOException {
		CompletableFuture<Void> watchdog = CompletableFuture.runAsync(() -> closeQuietly(body),
				CompletableFuture.delayedExecutor(DOCUMENT_READ_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
		try {
			return body.readNBytes(MAX_DOCUMENT_BYTES + 1);
		}
		finally {
			watchdog.cancel(false);
		}
	}

	private static void closeQuietly(InputStream body) {
		try {
			body.close();
		}
		catch (IOException ex) {
			// El lector recibe el error de la lectura interrumpida.
		}
	}

	private static ServiceUnavailableException unavailable() {
		return new ServiceUnavailableException("STORAGE_UNAVAILABLE",
				"El almacenamiento de archivos no esta disponible; intente mas tarde");
	}
}
