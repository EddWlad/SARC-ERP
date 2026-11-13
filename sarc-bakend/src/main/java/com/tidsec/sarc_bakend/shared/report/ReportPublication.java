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
			"periodKind", "periodType", "brand", "connectorId", "selectionBasis", "referenceWeek", "temporalSection",
			"periodPreset", "equipmentIds");
	private static final Set<String> PERIOD_KINDS = Set.of("OFFICIAL", "PROVISIONAL", "NONE");
	private static final Set<String> PERIOD_TYPES = Set.of("ROLLING_24H", "ROLLING_7D", "ROLLING_30D", "EXACT_WEEK");
	/** Periodos del dashboard de intermitencias; es una lista propia: no se mezcla con los de Uptime. */
	private static final Set<String> PERIOD_PRESETS = Set.of("FIRST_HALF", "SECOND_HALF", "LAST_15_DAYS", "CUSTOM");
	/** Puntos de un reporte de intermitencias: identificadores separados por comas. */
	private static final int MAX_EQUIPMENT_IDS = 200;

	public ReportPublication {
		Objects.requireNonNull(profile);
		Objects.requireNonNull(cutOff);
		criteria = Map.copyOf(criteria);
		if (!KEYS.containsAll(criteria.keySet())) {
			throw new IllegalArgumentException("Criterio de reporte no permitido");
		}
        if ("REFERENCE_WEEK".equals(criteria.get("selectionBasis"))) {
            LocalDate reference=LocalDate.parse(Objects.requireNonNull(criteria.get("referenceWeek")));
            if(reference.getDayOfWeek()!=java.time.DayOfWeek.MONDAY || criteria.containsKey("from") || criteria.containsKey("to")
                ||!"ROLLING_7D".equals(criteria.get("periodType"))||!"PROVISIONAL".equals(criteria.get("periodKind")))
                throw new IllegalArgumentException("Referencia semanal incompatible");
            if(criteria.containsKey("temporalSection")&&!Set.of("KNOWN_RANGE","REFERENCE_WEEK").contains(criteria.get("temporalSection")))
                throw new IllegalArgumentException("Apartado temporal inválido");
        } else {
            if(criteria.containsKey("referenceWeek")||criteria.containsKey("temporalSection")
                ||criteria.containsKey("selectionBasis")&&!"ACTUAL_RANGE".equals(criteria.get("selectionBasis")))
                throw new IllegalArgumentException("Selección incompatible");
            LocalDate from=LocalDate.parse(Objects.requireNonNull(criteria.get("from")));
            LocalDate to=LocalDate.parse(Objects.requireNonNull(criteria.get("to")));
            if(!to.isAfter(from))throw new IllegalArgumentException("Periodo de reporte inválido");
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
		if (criteria.containsKey("periodPreset") && !PERIOD_PRESETS.contains(criteria.get("periodPreset"))) {
			throw new IllegalArgumentException("Periodo predefinido inválido");
		}
		if (criteria.containsKey("equipmentIds")) {
			String[] ids = criteria.get("equipmentIds").split(",", -1);
			if (ids.length > MAX_EQUIPMENT_IDS) {
				throw new IllegalArgumentException("Demasiados equipos en el reporte");
			}
			for (String id : ids) {
				UUID.fromString(id);
			}
		}
		if (criteria.containsKey("brand") && criteria.get("brand").isBlank()) {
			throw new IllegalArgumentException("Marca inválida");
		}
		if (criteria.containsKey("connectorId")) {
			UUID.fromString(criteria.get("connectorId"));
		}
	}
}
