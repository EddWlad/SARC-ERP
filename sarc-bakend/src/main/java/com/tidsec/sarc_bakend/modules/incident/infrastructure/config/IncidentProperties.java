package com.tidsec.sarc_bakend.modules.incident.infrastructure.config;

import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Propiedades del modulo de incidencias (feature 010). El ambiente de numeracion es opcional: sin el, la
 * aplicacion arranca y solo se bloquean la configuracion de la numeracion y la creacion de tickets (research R2).
 */
@Validated
@ConfigurationProperties("sarc.incidents")
public record IncidentProperties(@Valid @DefaultValue Numbering numbering, @Valid @DefaultValue Evidence evidence) {

	public record Numbering(
			@Pattern(regexp = "\\s*|PRODUCTION|TEST", message = "debe ser PRODUCTION o TEST, o quedar vacio") String environment) {

		/** Ambiente del servidor; vacio si la propiedad falta o esta en blanco. */
		public Optional<String> expectedEnvironment() {
			return environment == null || environment.isBlank() ? Optional.empty() : Optional.of(environment);
		}
	}

	public record Evidence(@DefaultValue("10485760") @Min(1) long maxBytes) {
	}
}
