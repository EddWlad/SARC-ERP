package com.tidsec.sarc_bakend.modules.incident.api.request;

import com.tidsec.sarc_bakend.modules.incident.application.command.ConfigureNumberingCommand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /numbering}: la confirmacion debe repetir el codigo resultante, por ejemplo TICKET-086. */
public record ConfigureNumberingRequest(
		@NotNull @Min(1) @Max(999_999_999) Integer startNumber,
		@NotBlank @Pattern(regexp = "PRODUCTION|TEST") String environment,
		@NotBlank @Size(max = 20) String confirmation) {

	public ConfigureNumberingCommand toCommand() {
		return new ConfigureNumberingCommand(startNumber, environment, confirmation);
	}
}
