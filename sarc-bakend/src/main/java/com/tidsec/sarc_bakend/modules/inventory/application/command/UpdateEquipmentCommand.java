package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.inventory.domain.model.EquipmentDetails;

/** Edicion de los datos y conectores de un equipo; {@code version} es la que leyo el cliente. */
public record UpdateEquipmentCommand(UUID id, long version, EquipmentDetails details, List<UUID> connectorTypeIds) {
}
