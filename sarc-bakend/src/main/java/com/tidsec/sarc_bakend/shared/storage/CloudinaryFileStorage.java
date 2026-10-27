package com.tidsec.sarc_bakend.shared.storage;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

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

	private final Cloudinary cloudinary;
	private final String rootFolder;

	CloudinaryFileStorage(StorageProperties properties) {
		this.cloudinary = new Cloudinary(ObjectUtils.asMap(
				"cloud_name", properties.cloudName(),
				"api_key", properties.apiKey(),
				"api_secret", properties.apiSecret(),
				"secure", true));
		this.rootFolder = properties.rootFolder();
	}

	@Override
	public String store(String folder, byte[] content) {
		try {
			Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
					"type", DELIVERY_TYPE,
					"resource_type", "image",
					"folder", rootFolder + "/" + folder,
					"unique_filename", true,
					"overwrite", false));
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
			Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
					"type", DELIVERY_TYPE,
					"resource_type", "raw",
					"folder", rootFolder + "/" + folder,
					"filename_override", filename,
					"use_filename", true,
					"unique_filename", true,
					"overwrite", false));
			return (String) result.get("public_id");
		}
		catch (IOException | RuntimeException ex) {
			log.error("No se pudo subir el documento a Cloudinary", ex);
			throw unavailable();
		}
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

	private static ServiceUnavailableException unavailable() {
		return new ServiceUnavailableException("STORAGE_UNAVAILABLE",
				"El almacenamiento de archivos no esta disponible; intente mas tarde");
	}
}
