package com.tidsec.sarc_bakend.shared.storage;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciales de Cloudinary. Vacias = almacenamiento no disponible (las subidas responden 503),
 * salvo que {@code required} sea verdadero (perfil {@code prod}): entonces la aplicacion no arranca.
 */
@ConfigurationProperties("sarc.storage.cloudinary")
record StorageProperties(
		@DefaultValue("") String cloudName,
		@DefaultValue("") String apiKey,
		@DefaultValue("") String apiSecret,
		@DefaultValue("sarc") String rootFolder,
		@DefaultValue("false") boolean required) {

	boolean isConfigured() {
		return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
	}

	/** Nombres (no valores) de las propiedades de credenciales que estan vacias. */
	List<String> missing() {
		List<String> names = new ArrayList<>();
		if (cloudName.isBlank()) {
			names.add("sarc.storage.cloudinary.cloud-name");
		}
		if (apiKey.isBlank()) {
			names.add("sarc.storage.cloudinary.api-key");
		}
		if (apiSecret.isBlank()) {
			names.add("sarc.storage.cloudinary.api-secret");
		}
		return names;
	}
}
