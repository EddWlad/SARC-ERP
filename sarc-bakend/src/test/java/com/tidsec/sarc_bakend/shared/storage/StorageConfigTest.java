package com.tidsec.sarc_bakend.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

/**
 * Cloudinary obligatorio en produccion (FR-025): sin credenciales el contexto falla nombrando las
 * propiedades ausentes, sin incluir ningun valor. En desarrollo y pruebas sigue siendo opcional.
 */
class StorageConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of())
			.withUserConfiguration(Enabled.class);

	@EnableConfigurationProperties(StorageProperties.class)
	@Import(StorageConfig.class)
	static class Enabled {
	}

	@Test
	void requiredWithoutCredentialsFailsNamingMissingPropertiesWithoutValues() {
		runner.withPropertyValues(
				"sarc.storage.cloudinary.required=true",
				"sarc.storage.cloudinary.api-key=clave-secreta-de-prueba")
				.run(context -> {
					assertThat(context).hasFailed();
					String message = rootMessage(context.getStartupFailure());
					assertThat(message)
							.contains("sarc.storage.cloudinary.cloud-name")
							.contains("sarc.storage.cloudinary.api-secret")
							.doesNotContain("sarc.storage.cloudinary.api-key")
							.doesNotContain("clave-secreta-de-prueba");
				});
	}

	@Test
	void notRequiredWithoutCredentialsStartsWithStorageDisabled() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			FileStorage storage = context.getBean(FileStorage.class);
			assertThat(storage).isNotInstanceOf(CloudinaryFileStorage.class);
			assertThat(storage.accessUrl("cualquier-referencia")).isEmpty();
		});
	}

	@Test
	void requiredWithCompleteCredentialsUsesCloudinary() {
		runner.withPropertyValues(
				"sarc.storage.cloudinary.required=true",
				"sarc.storage.cloudinary.cloud-name=nube-de-prueba",
				"sarc.storage.cloudinary.api-key=clave-de-prueba",
				"sarc.storage.cloudinary.api-secret=secreto-de-prueba")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(FileStorage.class)).isInstanceOf(CloudinaryFileStorage.class);
				});
	}

	private static String rootMessage(Throwable failure) {
		Throwable current = failure;
		StringBuilder all = new StringBuilder();
		while (current != null) {
			all.append(current.getMessage()).append(' ');
			current = current.getCause();
		}
		return all.toString();
	}
}
