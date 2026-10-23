package com.tidsec.sarc_bakend.shared.web;

import java.util.ArrayList;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Contrato OpenAPI (docs/api/openapi.json), fuente de los tipos TypeScript del frontend.
 * Los DTOs de respuesta son records que siempre traen todos sus campos, salvo los marcados con
 * {@code @Schema(nullable = true)}; se declaran asi para que el cliente no los trate como opcionales.
 * Los requests no se tocan: su obligatoriedad sale de las anotaciones de validacion.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenApiCustomizer sarcOpenApiCustomizer() {
		return openApi -> {
			openApi.info(new Info().title("SARC API").version("v1")
					.description("Sistema Administrativo de Reportes de Carga"));
			Components components = openApi.getComponents();
			if (components != null && components.getSchemas() != null) {
				components.getSchemas().forEach((name, schema) -> requireNonNullableProperties(schema));
			}
		};
	}

	private static void requireNonNullableProperties(Schema<?> schema) {
		Map<String, Schema> properties = schema.getProperties();
		if (schema.getRequired() != null || properties == null) {
			return;
		}
		var required = new ArrayList<String>();
		properties.forEach((property, propertySchema) -> {
			if (!isNullable(propertySchema)) {
				required.add(property);
			}
		});
		schema.setRequired(required);
	}

	private static boolean isNullable(Schema<?> schema) {
		return Boolean.TRUE.equals(schema.getNullable()) || (schema.getTypes() != null && schema.getTypes().contains("null"));
	}
}
