package com.tidsec.sarc_bakend.modules.uptime.domain.repository;

import java.util.UUID;

/** Filtros del historial. {@code onlyEffective} es la proyeccion de los roles de consulta. */
public record MeasurementCriteria(UUID connectorId, UUID equipmentId, Queue queue, boolean onlyEffective) {

	/** Filtro derivado, distinto de {@code review_state}. */
	public enum Queue {
		PENDING,
		OBSERVED,
		VALIDATED,
		EXCLUDED
	}
}
