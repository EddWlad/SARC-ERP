package com.tidsec.sarc_bakend.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credenciales de Cloudinary. Vacias = almacenamiento no disponible (las subidas responden 503). */
@ConfigurationProperties("sarc.storage.cloudinary")
record StorageProperties(
		@DefaultValue("") String cloudName,
		@DefaultValue("") String apiKey,
		@DefaultValue("") String apiSecret,
		@DefaultValue("sarc") String rootFolder) {

	boolean isConfigured() {
		return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
	}
}
