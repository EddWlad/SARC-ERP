package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Connector;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentSearchCriteria;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/**
 * Implementacion del puerto {@link IncidentAssetDirectory} con los datos del inventario (feature 010). Solo
 * lectura. Nunca copia UID/PUK, URL OCPP ni serie al {@link IncidentAsset}.
 */
@Service
@Transactional(readOnly = true)
class IncidentAssetDirectoryService implements IncidentAssetDirectory {

	private static final Comparator<IncidentAsset> BY_NAME = Comparator
			.comparing((IncidentAsset asset) -> comparable(asset.name())).thenComparing(IncidentAsset::id);

	private final EquipmentRepository equipment;

	IncidentAssetDirectoryService(EquipmentRepository equipment) {
		this.equipment = equipment;
	}

	@Override
	public List<IncidentAsset> search(String text, int limit) {
		String wanted = comparable(text == null ? "" : text.strip());
		EquipmentSearchCriteria active = new EquipmentSearchCriteria(null, null, null, null,
				AdministrativeStatus.ACTIVE, null);
		return equipment.findAll(active).stream()
				.filter(item -> wanted.isEmpty() || comparable(item.getName()).contains(wanted)
						|| (item.getAssignedCode() != null && item.getAssignedCode().toLowerCase(Locale.ROOT)
								.contains(wanted)))
				.map(IncidentAssetDirectoryService::asset).sorted(BY_NAME).limit(Math.max(limit, 0)).toList();
	}

	@Override
	public Map<UUID, IncidentAsset> findByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return equipment.findAllById(Set.copyOf(ids)).stream().map(IncidentAssetDirectoryService::asset)
				.collect(Collectors.toMap(IncidentAsset::id, Function.identity()));
	}

	private static IncidentAsset asset(Equipment item) {
		List<IncidentConnector> connectors = item.allConnectors().stream()
				.sorted(Comparator.comparingInt(Connector::getOrdinal))
				.map(connector -> new IncidentConnector(connector.getId(), connector.getOrdinal(),
						connector.getConnectorType().getName(), connector.isActive()))
				.toList();
		return new IncidentAsset(item.getId(), item.getName(), item.getAssignedCode(), item.getNetwork(),
				item.getType(), item.getLocation(), item.getMapsUrl(), item.getAdminStatus(), connectors);
	}

	/** Minusculas y sin tildes, para buscar sin distinguir ni mayusculas ni acentos. */
	private static String comparable(String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT);
	}
}
