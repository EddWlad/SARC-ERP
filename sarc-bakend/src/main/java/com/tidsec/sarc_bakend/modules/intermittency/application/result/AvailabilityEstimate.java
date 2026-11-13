package com.tidsec.sarc_bakend.modules.intermittency.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Estimate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodPresets.Period;

/**
 * Disponibilidad estimada de prueba de un periodo (FR-042 a FR-046a): el resultado del calculo, lo que queda
 * fuera del alcance con su motivo, el origen de los datos y sus limitaciones. Solo la ven quienes tienen el
 * permiso de disponibilidad; es una estimacion, no una medicion de Enerlink ni de Uptime.
 */
public record AvailabilityEstimate(Period period, Instant cutOff, Estimate estimate, List<Exclusion> exclusions,
		String origin, List<String> limitations) {

	/** Por que un punto no entra en la disponibilidad estimada. */
	public enum ExclusionReason {
		/** Instalado sin servicio (DESCONECTADO). */
		DISCONNECTED,
		/** Inactivo y sin ventanas en el rango. */
		INACTIVE,
		/** Eliminado y sin ventanas en el rango. */
		DELETED
	}

	public record Exclusion(UUID equipmentId, String code, String name, ExclusionReason reason) {
	}
}
