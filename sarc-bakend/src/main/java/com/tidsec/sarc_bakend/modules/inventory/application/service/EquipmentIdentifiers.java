package com.tidsec.sarc_bakend.modules.inventory.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.modules.inventory.domain.repository.EquipmentRepository;
import com.tidsec.sarc_bakend.shared.error.ConflictException;

/**
 * Serie, codigo y UID/PUK son unicos en todo el inventario, eliminados incluidos (RF-002.2/3). Si el
 * dueno es un equipo eliminado se indica su id para recuperarlo en vez de duplicarlo; si los
 * identificadores pertenecen a equipos distintos se detiene sin fusionar identidades (PRD 6.3).
 */
@Component
class EquipmentIdentifiers {

	private final EquipmentRepository equipment;

	EquipmentIdentifiers(EquipmentRepository equipment) {
		this.equipment = equipment;
	}

	/**
	 * Rechaza {@code candidate} si alguno de sus identificadores ya es de otro equipo.
	 * {@code ownerId} es el equipo que se esta editando (sus propios identificadores no cuentan).
	 */
	void requireUnique(Equipment candidate, UUID ownerId) {
		List<Equipment> holders = equipment
				.findHoldersOf(candidate.getSerial(), candidate.getAssignedCode(), candidate.getUidPuk()).stream()
				.filter(holder -> !holder.getId().equals(ownerId))
				.toList();
		if (holders.isEmpty()) {
			return;
		}
		if (holders.size() > 1) {
			throw new ConflictException("IDENTIFIERS_BELONG_TO_DIFFERENT_EQUIPMENT",
					"La serie, el codigo o el UID/PUK pertenecen a equipos distintos; revise los datos",
					Map.of("equipmentIds", holders.stream().map(Equipment::getId).toList()));
		}
		Equipment holder = holders.getFirst();
		if (holder.isDeleted()) {
			throw new ConflictException("EQUIPMENT_DELETED_EXISTS",
					"Ya existe un equipo eliminado con esos identificadores; recuperelo en lugar de crear otro",
					Map.of("equipmentId", holder.getId()));
		}
		throw new ConflictException("EQUIPMENT_IDENTIFIER_IN_USE",
				"La serie, el codigo o el UID/PUK ya pertenecen a otro equipo", Map.of("equipmentId", holder.getId()));
	}
}
