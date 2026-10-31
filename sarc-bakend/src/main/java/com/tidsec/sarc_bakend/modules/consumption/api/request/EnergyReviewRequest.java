package com.tidsec.sarc_bakend.modules.consumption.api.request;

import java.util.UUID;
import com.tidsec.sarc_bakend.modules.consumption.application.command.DecideEnergyReviewCommand;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnergyReviewRequest(
		@NotNull(message = "Seleccione aprobar o rechazar la energía") EnergyReviewStatus decision,
		@NotBlank(message = "Ingrese una justificación")
		@Size(max = 500, message = "La justificación admite hasta 500 caracteres") String justification,
		@NotNull(message = "Indique la versión revisada") Long version) {
	public EnergyReviewRequest {
		justification = justification == null ? null : justification.strip();
	}
	public DecideEnergyReviewCommand toCommand(UUID id) {
		return new DecideEnergyReviewCommand(id, decision, justification, version);
	}
}
