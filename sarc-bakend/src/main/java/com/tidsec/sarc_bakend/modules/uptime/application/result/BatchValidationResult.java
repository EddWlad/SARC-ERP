package com.tidsec.sarc_bakend.modules.uptime.application.result;

import java.util.List;
import java.util.UUID;

/** Un item omitido no revierte los demas. */
public record BatchValidationResult(int validated, List<Skipped> skipped) {

	public record Skipped(UUID id, Reason reason) {
	}

	public enum Reason {
		OBSERVED,
		STALE_VERSION,
		NOT_PENDING,
		NOT_FOUND
	}
}
