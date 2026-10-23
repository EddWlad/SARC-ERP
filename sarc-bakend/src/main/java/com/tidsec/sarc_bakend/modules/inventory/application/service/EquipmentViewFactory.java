package com.tidsec.sarc_bakend.modules.inventory.application.service;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.application.result.EquipmentView;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment.Equipment;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;

/** Arma la vista de un equipo con la URL firmada de su imagen y los datos que el usuario puede ver. */
@Component
class EquipmentViewFactory {

	private final FileStorage fileStorage;
	private final CurrentActor currentActor;

	EquipmentViewFactory(FileStorage fileStorage, CurrentActor currentActor) {
		this.fileStorage = fileStorage;
		this.currentActor = currentActor;
	}

	EquipmentView forCurrentUser(Equipment equipment) {
		return create(equipment, currentActor.hasAnyRole(InventoryPermissions.RESTRICTED_DATA_VIEWERS));
	}

	/** Para reportes generales: nunca incluyen UID/PUK ni URL OCPP (RF-002.7). */
	EquipmentView withoutRestrictedData(Equipment equipment) {
		return create(equipment, false);
	}

	private EquipmentView create(Equipment equipment, boolean restrictedVisible) {
		String imageUrl = equipment.getImageRef() == null ? null
				: fileStorage.accessUrl(equipment.getImageRef()).orElse(null);
		return EquipmentView.from(equipment, imageUrl, restrictedVisible);
	}
}
