package com.tidsec.sarc_bakend.modules.uptime.api.response;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.uptime.application.result.ClassificationResult;

public record ClassificationResponse(UUID connectorId, ClassificationResult.Outcome result, UUID measurementId) {

	public static ClassificationResponse from(ClassificationResult result) {
		return new ClassificationResponse(result.connectorId(), result.result(), result.measurementId());
	}
}
