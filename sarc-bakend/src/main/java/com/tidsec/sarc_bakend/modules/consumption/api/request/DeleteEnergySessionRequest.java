package com.tidsec.sarc_bakend.modules.consumption.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Baja logica de una sesion: version revisada y motivo obligatorio. */
public record DeleteEnergySessionRequest(
		@NotNull(message = "Indique la versión revisada") Long version,
		@NotBlank(message = "Ingrese el motivo de la eliminación")
		@Size(max = 500, message = "El motivo admite hasta 500 caracteres") String reason) {

	public DeleteEnergySessionRequest {
		reason = reason == null ? null : reason.strip();
	}
}
