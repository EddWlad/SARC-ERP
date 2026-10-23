package com.tidsec.sarc_bakend.shared.storage;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tidsec.sarc_bakend.shared.error.ServiceUnavailableException;

@Configuration(proxyBeanMethods = false)
class StorageConfig {

	private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

	/**
	 * Sin credenciales la aplicacion arranca igual (desarrollo, CI): solo fallan las operaciones
	 * que necesitan almacenamiento, con un 503 explicito.
	 */
	@Bean
	FileStorage fileStorage(StorageProperties properties) {
		if (properties.isConfigured()) {
			return new CloudinaryFileStorage(properties);
		}
		log.warn("Cloudinary no configurado (sarc.storage.cloudinary.*): la subida de archivos esta deshabilitada");
		return new FileStorage() {
			@Override
			public String store(String folder, byte[] content) {
				throw notConfigured();
			}

			@Override
			public void delete(String reference) {
				throw notConfigured();
			}

			@Override
			public Optional<String> accessUrl(String reference) {
				return Optional.empty();
			}
		};
	}

	private static ServiceUnavailableException notConfigured() {
		return new ServiceUnavailableException("STORAGE_NOT_CONFIGURED",
				"El almacenamiento de archivos no esta configurado en este entorno");
	}
}
