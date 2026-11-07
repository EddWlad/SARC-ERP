package com.tidsec.sarc_bakend.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Lee {@code application-prod.yaml} tal cual (sin interpolar variables) y comprueba las decisiones de
 * produccion de la Feature 007: Cloudinary obligatorio, Swagger y api-docs apagados, cabeceras
 * reenviadas por Nginx y ningun valor por omision para las credenciales de la base de datos.
 */
class ProductionProfileConfigTest {

	private static final PropertySource<?> PROD = load();

	private static PropertySource<?> load() {
		try {
			List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("prod",
					new ClassPathResource("application-prod.yaml"));
			return sources.get(0);
		}
		catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void cloudinaryIsRequired() {
		assertThat(PROD.getProperty("sarc.storage.cloudinary.required")).isEqualTo(true);
	}

	@Test
	void swaggerAndApiDocsAreDisabled() {
		assertThat(PROD.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
		assertThat(PROD.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
	}

	@Test
	void forwardHeadersAreHandledNatively() {
		assertThat(PROD.getProperty("server.forward-headers-strategy")).isEqualTo("native");
	}

	@Test
	void databaseSettingsHaveNoDefaults() {
		for (String key : List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password")) {
			Object value = PROD.getProperty(key);
			assertThat(value).as(key).isNotNull();
			// Sin valor por omision: ${VARIABLE} y nunca ${VARIABLE:valor}.
			assertThat(value.toString()).as(key).matches("\\$\\{SARC_DB_[A-Z]+}");
		}
	}

	@Test
	void productionFolderDefaultsToSarcProd() {
		assertThat(PROD.getProperty("sarc.storage.cloudinary.root-folder"))
				.isEqualTo("${SARC_STORAGE_CLOUDINARY_ROOT_FOLDER:sarc-prod}");
	}
}
