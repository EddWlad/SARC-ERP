package com.tidsec.sarc_bakend.shared.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Metadatos neutrales entre módulos; no serializa entidades ni criterios privados de consumo. */
public record ReportPublication(ReportContentProfile profile, Instant cutOff, Map<String, String> criteria) {

	/** Zona operativa en la que se interpretan los días del periodo. */
	public static final String TIME_ZONE = "America/Guayaquil";

	private static final Set<String> KEYS = Set.of("from", "to", "network", "type", "source", "equipmentId", "timeZone",
			"periodKind", "periodType", "brand", "connectorId");
	private static final Set<String> PERIOD_KINDS = Set.of("OFFICIAL", "PROVISIONAL", "NONE");
	private static final Set<String> PERIOD_TYPES = Set.of("ROLLING_24H", "ROLLING_7D", "ROLLING_30D", "EXACT_WEEK");

	public ReportPublication {
		Objects.requireNonNull(profile);
		Objects.requireNonNull(cutOff);
		criteria = Map.copyOf(criteria);
		if (!KEYS.containsAll(criteria.keySet())) {
			throw new IllegalArgumentException("Criterio de reporte no permitido");
		}
		LocalDate from = LocalDate.parse(Objects.requireNonNull(criteria.get("from")));
		LocalDate to = LocalDate.parse(Objects.requireNonNull(criteria.get("to")));
		if (!to.isAfter(from)) {
			throw new IllegalArgumentException("Periodo de reporte inválido");
		}
		if (!TIME_ZONE.equals(criteria.get("timeZone"))) {
			throw new IllegalArgumentException("Zona operativa inválida");
		}
		if (criteria.containsKey("network")) {
			ChargingNetwork.valueOf(criteria.get("network"));
		}
		if (criteria.containsKey("type")) {
			InfrastructureType.valueOf(criteria.get("type"));
		}
		if (criteria.containsKey("source") && !Set.of("ENERLINK", "WALLBOX").contains(criteria.get("source"))) {
			throw new IllegalArgumentException("Fuente inválida");
		}
		if (criteria.containsKey("equipmentId")) {
			UUID.fromString(criteria.get("equipmentId"));
		}
		if (criteria.containsKey("periodKind") && !PERIOD_KINDS.contains(criteria.get("periodKind"))) {
			throw new IllegalArgumentException("Clase de periodo inválida");
		}
		if (criteria.containsKey("periodType") && !PERIOD_TYPES.contains(criteria.get("periodType"))) {
			throw new IllegalArgumentException("Tipo de periodo inválido");
		}
		if (criteria.containsKey("brand") && criteria.get("brand").isBlank()) {
			throw new IllegalArgumentException("Marca inválida");
		}
		if (criteria.containsKey("connectorId")) {
			UUID.fromString(criteria.get("connectorId"));
		}
	}
}
