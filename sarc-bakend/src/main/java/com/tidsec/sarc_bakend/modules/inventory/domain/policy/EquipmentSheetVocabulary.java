package com.tidsec.sarc_bakend.modules.inventory.domain.policy;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.EquipmentBrand;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.UsageStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;

/**
 * Valores que acepta la plantilla de carga masiva: la etiqueta en espanol que muestra la plantilla o
 * el nombre interno, sin importar mayusculas, tildes ni espacios repetidos. La plantilla ofrece
 * exactamente estas etiquetas en sus listas desplegables.
 */
public final class EquipmentSheetVocabulary {

	public static final Map<ChargingNetwork, String> NETWORKS = labels(ChargingNetwork.class,
			Map.of(ChargingNetwork.KIA, "KIA", ChargingNetwork.JAC, "JAC", ChargingNetwork.CONECTZ, "CONECTZ"));
	public static final Map<InfrastructureType, String> TYPES = labels(InfrastructureType.class,
			Map.of(InfrastructureType.CHARGING_POINT, "Punto de carga",
					InfrastructureType.CHARGING_STATION, "Electrolinera"));
	public static final Map<EquipmentBrand, String> BRANDS = labels(EquipmentBrand.class,
			Map.of(EquipmentBrand.BENNY, "Benny", EquipmentBrand.WALLBOX, "Wallbox"));
	public static final Map<UsageStatus, String> USAGE_STATUSES = labels(UsageStatus.class,
			Map.of(UsageStatus.FREE, "Libre", UsageStatus.BLOCKED, "Bloqueado",
					UsageStatus.DISCONNECTED, "Desconectado"));

	private EquipmentSheetVocabulary() {
	}

	/** El valor cuya etiqueta o nombre coincide con {@code text}; vacio si no coincide ninguno. */
	public static <E extends Enum<E>> Optional<E> parse(String text, Map<E, String> labels) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String wanted = HeaderLayout.normalize(text);
		return labels.entrySet().stream()
				.filter(entry -> HeaderLayout.normalize(entry.getValue()).equals(wanted)
						|| entry.getKey().name().equals(wanted.replace(' ', '_')))
				.map(Map.Entry::getKey)
				.findFirst();
	}

	/** Etiquetas aceptadas, separadas por coma, para los mensajes de error. */
	public static String accepted(Map<?, String> labels) {
		return String.join(", ", labels.values());
	}

	/** Exige una etiqueta para cada valor del enum, en su orden de declaracion. */
	private static <E extends Enum<E>> Map<E, String> labels(Class<E> type, Map<E, String> labels) {
		Map<E, String> ordered = new EnumMap<>(type);
		Arrays.stream(type.getEnumConstants()).forEach(value -> ordered.put(value,
				Objects.requireNonNull(labels.get(value), () -> "Falta la etiqueta de " + value)));
		return Collections.unmodifiableMap(ordered);
	}
}
