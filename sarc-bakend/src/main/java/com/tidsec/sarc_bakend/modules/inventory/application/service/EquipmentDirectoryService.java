package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.externalidentity.ExternalIdentity;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.ExternalIdentityRepository;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

/** Implementacion del puerto publico {@link EquipmentDirectory} con los datos del inventario. */
@Service
@Transactional(readOnly = true)
class EquipmentDirectoryService implements EquipmentDirectory {

	private final EquipmentRepository equipment;
	private final ExternalIdentityRepository identities;

	EquipmentDirectoryService(EquipmentRepository equipment, ExternalIdentityRepository identities) {
		this.equipment = equipment;
		this.identities = identities;
	}

	@Override
	public Map<String, EquipmentReference> findBySerials(Collection<String> serials) {
		Map<String, String> requestedByNormalized = new HashMap<>();
		for (String serial : serials) {
			String normalized = Equipment.normalizeIdentifier(serial);
			if (normalized != null) {
				requestedByNormalized.putIfAbsent(normalized, serial);
			}
		}
		if (requestedByNormalized.isEmpty()) {
			return Map.of();
		}
		return equipment.findBySerials(requestedByNormalized.keySet()).stream()
				.collect(Collectors.toMap(found -> requestedByNormalized.get(found.getSerial()),
						EquipmentDirectoryService::reference));
	}

	@Override
	public Map<ExternalKey, EquipmentReference> findByVerifiedIdentities(ExternalPlatform platform,
			Collection<ExternalKey> keys) {
		// La comparacion ignora tildes, y PostgreSQL no lo hace sin la extension unaccent: por eso se
		// cargan las verificadas de la plataforma (cientos), pero solo si hay algo que buscar.
		if (keys.isEmpty()) {
			return Map.of();
		}
		Map<String, UUID> equipmentByKey = new HashMap<>();
		for (ExternalIdentity identity : identities.findVerified(platform)) {
			equipmentByKey.put(new ExternalKey(identity.getScope(), identity.getExternalId()).comparable(),
					identity.getEquipmentId());
		}
		Map<ExternalKey, UUID> matched = new HashMap<>();
		for (ExternalKey key : keys) {
			UUID equipmentId = equipmentByKey.get(key.comparable());
			if (equipmentId != null) {
				matched.put(key, equipmentId);
			}
		}
		Map<UUID, EquipmentReference> references = findByIds(matched.values());
		Map<ExternalKey, EquipmentReference> result = new HashMap<>();
		matched.forEach((key, id) -> result.put(key, references.get(id)));
		return result;
	}

	@Override
	public Map<UUID, EquipmentReference> findByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<Equipment> found = equipment.findAllById(Set.copyOf(ids));
		return found.stream().map(EquipmentDirectoryService::reference)
				.collect(Collectors.toMap(EquipmentReference::id, Function.identity()));
	}

	private static EquipmentReference reference(Equipment item) {
		return new EquipmentReference(item.getId(), item.getNetwork(), item.getType(), item.getName(),
				item.getAssignedCode());
	}
}
