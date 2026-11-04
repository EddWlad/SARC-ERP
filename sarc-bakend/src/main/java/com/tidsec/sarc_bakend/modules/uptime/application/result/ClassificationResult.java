package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.util.UUID;

/** Resultado por conector. {@code DIFFERS}: habia otra medicion y no se sobrescribio. */
public record ClassificationResult(UUID connectorId, Outcome result, UUID measurementId) {

	public enum Outcome {
		CREATED,
		IDENTICAL,
		DIFFERS
	}
}
