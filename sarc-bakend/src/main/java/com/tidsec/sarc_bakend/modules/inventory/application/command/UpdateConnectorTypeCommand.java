package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.UUID;

import com.tidsec.sarc_bakend.shared.domain.AdministrativeStatus;

/** Cambios del catalogo de conectores; los campos nulos no se modifican. */
public record UpdateConnectorTypeCommand(UUID id, long version, String name, AdministrativeStatus status) {
}
