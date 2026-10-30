package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;

/**
 * Identidades Enerlink agrupadas por su par normalizado (D05). La comparacion ignora tildes, y
 * PostgreSQL no lo hace sin extensiones: se cargan una vez por clasificacion o tramo, no por fila.
 */
final class EnerlinkIdentityIndex {

	private final Map<String, List<ExternalIdentity>> byKey;

	private EnerlinkIdentityIndex(Map<String, List<ExternalIdentity>> byKey) {
		this.byKey = byKey;
	}

	static EnerlinkIdentityIndex of(Collection<ExternalIdentity> identities) {
		return new EnerlinkIdentityIndex(identities.stream().collect(Collectors.groupingBy(
				EnerlinkIdentityIndex::keyOf)));
	}

	/** Identidades equivalentes al par indicado, sea cual sea su escritura. */
	List<ExternalIdentity> holders(ProposedEquipment.Identity identity) {
		return byKey.getOrDefault(new ExternalKey(identity.scope(), identity.externalId()).comparable(), List.of());
	}

	/** El equipo ya tiene otro par Enerlink verificado, distinto del indicado. */
	boolean hasOtherVerifiedPair(UUID equipmentId, ProposedEquipment.Identity identity) {
		String key = new ExternalKey(identity.scope(), identity.externalId()).comparable();
		return byKey.entrySet().stream().filter(entry -> !entry.getKey().equals(key))
				.flatMap(entry -> entry.getValue().stream())
				.anyMatch(existing -> existing.isVerified() && existing.getEquipmentId().equals(equipmentId));
	}

	private static String keyOf(ExternalIdentity identity) {
		return new ExternalKey(identity.getScope(), identity.getExternalId()).comparable();
	}
}
