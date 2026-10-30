package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.LinkIssue;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.catalog.IdentityMatch;

/**
 * Identifica el equipo del inventario de cada sesion, solo con claves confiables (PRD RF-003.7: nunca
 * por coincidencia debil ni por el nombre del archivo):
 * <ul>
 * <li>Enerlink: ACTIVO + CARGADOR completos, verificados en un unico equipo;</li>
 * <li>Wallbox: identidad externa verificada o serie exacta del equipo.</li>
 * </ul>
 * Lo que no se resuelve queda pendiente de vinculacion, con su motivo cuando la fuente es Enerlink.
 */
@Component
class EquipmentResolver {

	/** Equipo encontrado, o motivo por el que no lo hay; ambos nulos si la fuente no informa motivo. */
	record Resolution(EquipmentReference equipment, LinkIssue issue) {
	}

	private final EquipmentDirectory directory;

	EquipmentResolver(EquipmentDirectory directory) {
		this.directory = directory;
	}

	/** Clave de la fuente guardada en una sesion, para volver a intentar su vinculacion. */
	static EquipmentLookup lookupOf(EnergySession session) {
		return lookupOf(session.getSource(), session.reading());
	}

	static EquipmentLookup lookupOf(SessionSource source, SessionReading reading) {
		return switch (source) {
			case WALLBOX -> new EquipmentLookup.BySerial(reading.serial());
			case ENERLINK -> new EquipmentLookup.ByIdentity(new ExternalKey(reading.site(), reading.charger()));
		};
	}

	/** Detalle de auditoria de una vinculacion; {@code origin} es el lote o la operacion que la hizo. */
	static Map<String, String> linkDetails(EnergySession session, String origin) {
		SessionReading reading = session.reading();
		Map<String, String> details = new HashMap<>(Map.of("equipmentId", session.getEquipmentId().toString(), "origin",
				origin));
		if (reading.site() != null) {
			details.put("site", reading.site());
		}
		if (reading.charger() != null) {
			details.put("charger", reading.charger());
		}
		if (reading.serial() != null) {
			details.put("serial", reading.serial());
		}
		return details;
	}

	/** Una entrada por cada clave recibida, resuelta o no. */
	Map<EquipmentLookup, Resolution> resolve(SessionSource source, Collection<EquipmentLookup> lookups) {
		Map<EquipmentLookup, Resolution> resolved = new HashMap<>();
		List<ExternalKey> pairs = new ArrayList<>();
		List<EquipmentLookup.BySerial> serials = new ArrayList<>();
		for (EquipmentLookup lookup : lookups) {
			switch (lookup) {
				case EquipmentLookup.ByIdentity identity -> {
					if (identity.key().isComplete()) {
						pairs.add(identity.key());
					}
					else {
						resolved.put(lookup, new Resolution(null, LinkIssue.IDENTITY_INCOMPLETE));
					}
				}
				case EquipmentLookup.BySerial bySerial -> serials.add(bySerial);
			}
		}
		directory.matchIdentities(source.platform(), pairs).forEach((key, match) -> resolved
				.put(new EquipmentLookup.ByIdentity(key), resolution(match)));
		resolved.putAll(resolveSerials(source, serials));
		return resolved;
	}

	private static Resolution resolution(IdentityMatch match) {
		return switch (match.status()) {
			case UNIQUE -> new Resolution(match.equipment(), null);
			case AMBIGUOUS -> new Resolution(null, LinkIssue.IDENTITY_AMBIGUOUS);
			case NOT_VERIFIED -> new Resolution(null, LinkIssue.IDENTITY_NOT_VERIFIED);
			case NOT_REGISTERED -> new Resolution(null, LinkIssue.IDENTITY_NOT_REGISTERED);
		};
	}

	private Map<EquipmentLookup, Resolution> resolveSerials(SessionSource source,
			List<EquipmentLookup.BySerial> serials) {
		Map<ExternalKey, EquipmentLookup.BySerial> byKey = new HashMap<>();
		serials.forEach(lookup -> byKey.put(new ExternalKey("", lookup.serial()), lookup));
		Map<EquipmentLookup, Resolution> resolved = new HashMap<>();
		directory.findByVerifiedIdentities(source.platform(), byKey.keySet())
				.forEach((key, equipment) -> resolved.put(byKey.get(key), new Resolution(equipment, null)));
		List<String> unresolved = serials.stream().filter(lookup -> !resolved.containsKey(lookup))
				.map(EquipmentLookup.BySerial::serial).toList();
		directory.findBySerials(unresolved).forEach((serial, equipment) -> resolved
				.put(new EquipmentLookup.BySerial(serial), new Resolution(equipment, null)));
		serials.forEach(lookup -> resolved.putIfAbsent(lookup, new Resolution(null, null)));
		return resolved;
	}
}
