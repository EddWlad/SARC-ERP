package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;

/**
 * Identifica el equipo del inventario de cada sesion, solo con claves confiables (PRD RF-003.7: nunca
 * por coincidencia debil ni por el nombre del archivo):
 * <ul>
 * <li>identidad externa VERIFICADA de la plataforma (Enerlink: ACTIVO + CARGADOR; Wallbox: serie);</li>
 * <li>para series, ademas, la serie exacta del equipo.</li>
 * </ul>
 * Lo que no se resuelve queda pendiente de vinculacion.
 */
@Component
class EquipmentResolver {

	private final EquipmentDirectory directory;

	EquipmentResolver(EquipmentDirectory directory) {
		this.directory = directory;
	}

	/** Clave de la fuente guardada en una sesion, para volver a intentar su vinculacion. */
	static EquipmentLookup lookupOf(EnergySession session) {
		return switch (session.getSource()) {
			case WALLBOX -> new EquipmentLookup.BySerial(session.reading().serial());
			case ENERLINK -> new EquipmentLookup.ByIdentity(
					new ExternalKey(session.reading().site(), session.reading().charger()));
		};
	}

	Map<EquipmentLookup, EquipmentReference> resolve(SessionSource source, Collection<EquipmentLookup> lookups) {
		Map<ExternalKey, EquipmentLookup> byKey = new HashMap<>();
		List<String> serials = new ArrayList<>();
		for (EquipmentLookup lookup : lookups) {
			switch (lookup) {
				case EquipmentLookup.ByIdentity identity -> byKey.put(identity.key(), lookup);
				case EquipmentLookup.BySerial bySerial -> {
					byKey.put(new ExternalKey("", bySerial.serial()), lookup);
					serials.add(bySerial.serial());
				}
			}
		}
		Map<EquipmentLookup, EquipmentReference> resolved = new HashMap<>();
		directory.findByVerifiedIdentities(source.platform(), byKey.keySet())
				.forEach((key, equipment) -> resolved.put(byKey.get(key), equipment));
		List<String> unresolvedSerials = serials.stream()
				.filter(serial -> !resolved.containsKey(new EquipmentLookup.BySerial(serial))).toList();
		directory.findBySerials(unresolvedSerials)
				.forEach((serial, equipment) -> resolved.put(new EquipmentLookup.BySerial(serial), equipment));
		return resolved;
	}
}
