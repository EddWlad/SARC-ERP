package com.tidsec.sarc_bakend.modules.uptime.application.result;

/** Resultado del alta: creada o reconocida como identica a la existente. */
public record RegistrationResult(Outcome outcome, MeasurementView view) {

	public enum Outcome {
		CREATED,
		IDENTICAL
	}
}
