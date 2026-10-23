package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Activar, inactivar, eliminar (logico) o recuperar un equipo. */
public record ChangeEquipmentStatusCommand(UUID id, long version, AdministrativeStatus status) {
}
