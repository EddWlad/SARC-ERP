package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointDirectory;
import com.tidsec.sarc_bakend.shared.catalog.KiaPointReference;
import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Implementacion del puerto {@link KiaPointDirectory} con los datos del inventario. Solo lectura. El Codigo
 * asignado se compara en forma literal, sin los espacios exteriores (politica D04): es equivalente a la
 * normalizacion de D05 porque V008 exige seis digitos.
 */
@Service
@Transactional(readOnly = true)
class KiaPointDirectoryService implements KiaPointDirectory {

	private static final Comparator<KiaPointReference> BY_NAME = Comparator
			.comparing((KiaPointReference point) -> comparable(point.name())).thenComparing(KiaPointReference::id);

	private final EquipmentRepository equipment;

	KiaPointDirectoryService(EquipmentRepository equipment) {
		this.equipment = equipment;
	}

	@Override
	public Map<String, List<KiaPointReference>> findByAssignedCodes(Collection<String> codes) {
		Set<String> requested = codes.stream().filter(code -> code != null && !code.isBlank()).map(String::strip)
				.collect(Collectors.toSet());
		if (requested.isEmpty()) {
			return Map.of();
		}
		Map<String, List<KiaPointReference>> found = new HashMap<>();
		for (Equipment item : equipment.findByAssignedCodes(requested)) {
			found.computeIfAbsent(item.getAssignedCode(), code -> new java.util.ArrayList<>()).add(reference(item));
		}
		found.values().forEach(points -> points.sort(BY_NAME));
		return found;
	}

	@Override
	public Map<UUID, KiaPointReference> findByIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return equipment.findAllById(Set.copyOf(ids)).stream().map(KiaPointDirectoryService::reference)
				.collect(Collectors.toMap(KiaPointReference::id, Function.identity()));
	}

	@Override
	public List<KiaPointReference> search(String text, int limit) {
		String wanted = comparable(text == null ? "" : text.strip());
		return equipment.findKiaChargingPoints().stream()
				.filter(item -> item.getAdminStatus() != AdministrativeStatus.DELETED)
				.filter(item -> wanted.isEmpty() || comparable(item.getName()).contains(wanted)
						|| (item.getAssignedCode() != null && item.getAssignedCode().contains(wanted)))
				.map(KiaPointDirectoryService::reference).sorted(BY_NAME).limit(Math.max(limit, 0)).toList();
	}

	@Override
	public List<KiaPointReference> allKiaPoints() {
		return equipment.findKiaChargingPoints().stream().map(KiaPointDirectoryService::reference).sorted(BY_NAME)
				.toList();
	}

	@Override
	public List<KiaPointReference> inScope(Instant at) {
		return equipment.findKiaChargingPoints().stream()
				.filter(item -> item.getAdminStatus() == AdministrativeStatus.ACTIVE)
				.filter(item -> !item.getCreatedAt().isAfter(at)).map(KiaPointDirectoryService::reference)
				.sorted(BY_NAME).toList();
	}

	private static KiaPointReference reference(Equipment item) {
		boolean inScope = item.getNetwork() == ChargingNetwork.KIA && item.getType() == InfrastructureType.CHARGING_POINT;
		return new KiaPointReference(item.getId(), item.getName(), item.getAssignedCode(),
				item.getUsageStatus() == null ? null : item.getUsageStatus().name(), item.getAdminStatus(), inScope,
				item.getCreatedAt());
	}

	/** Minusculas y sin tildes: PostgreSQL no compara asi sin la extension unaccent. */
	private static String comparable(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}
}
