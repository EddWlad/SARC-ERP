package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.ScheduleException;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.WeeklyInterval;

/**
 * Valores anteriores y nuevos de un equipo para la auditoria (PRD RN-G-002). UID/PUK y URL OCPP son
 * informacion restringida: de ellos solo se registra que cambiaron, nunca su valor.
 */
final class EquipmentChanges {

	private static final Set<String> RESTRICTED = Set.of("uidPuk", "ocppUrl");

	private EquipmentChanges() {
	}

	/** Campos editables del equipo; se toma antes y despues del cambio y se compara con {@link #diff}. */
	static Map<String, String> snapshot(Equipment equipment) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("name", equipment.getName());
		values.put("brand", text(equipment.getBrand()));
		values.put("serial", equipment.getSerial());
		values.put("assignedCode", equipment.getAssignedCode());
		values.put("location", equipment.getLocation());
		values.put("mapsUrl", equipment.getMapsUrl());
		values.put("usageStatus", text(equipment.getUsageStatus()));
		values.put("connectors", equipment.activeConnectors().stream()
				.sorted(Comparator.comparingInt(Connector::getOrdinal))
				.map(connector -> connector.getConnectorType().getCode())
				.collect(Collectors.joining(", ")));
		values.put("uidPuk", equipment.getUidPuk());
		values.put("ocppUrl", equipment.getOcppUrl());
		return values;
	}

	/** Horario legible en una linea: modo, intervalos y excepciones por fecha. */
	static Map<String, String> schedule(Equipment equipment) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("mode", equipment.getScheduleMode().name());
		values.put("intervals", equipment.getWeeklyIntervals().stream().map(EquipmentChanges::interval)
				.collect(Collectors.joining("; ")));
		values.put("exceptions", equipment.getScheduleExceptions().stream().map(EquipmentChanges::exception)
				.collect(Collectors.joining("; ")));
		return values;
	}

	/** Solo lo que cambio: {@code campoFrom}/{@code campoTo}, o {@code campoChanged} si es restringido. */
	static Map<String, String> diff(Map<String, String> before, Map<String, String> after) {
		Map<String, String> changes = new LinkedHashMap<>();
		after.forEach((field, value) -> {
			String previous = before.get(field);
			if (Objects.equals(previous, value)) {
				return;
			}
			if (RESTRICTED.contains(field)) {
				changes.put(field + "Changed", "true");
			}
			else {
				changes.put(field + "From", previous == null ? "" : previous);
				changes.put(field + "To", value == null ? "" : value);
			}
		});
		return changes;
	}

	private static String interval(WeeklyInterval interval) {
		return interval.dayOfWeek() + " " + interval.opensAt() + "-" + interval.closesAt();
	}

	private static String exception(ScheduleException exception) {
		String hours = exception.closed() ? "cerrado" : exception.opensAt() + "-" + exception.closesAt();
		return exception.date() + " " + hours;
	}

	private static String text(Enum<?> value) {
		return value == null ? null : value.name();
	}
}
