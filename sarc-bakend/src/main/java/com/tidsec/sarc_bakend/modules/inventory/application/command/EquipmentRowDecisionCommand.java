package com.tidsec.sarc_bakend.modules.inventory.application.command;

import java.util.List;
import java.util.UUID;

/** Aprueba (o retira la aprobacion de) la actualizacion de equipos existentes en la vista previa. */
public record EquipmentRowDecisionCommand(UUID batchId, List<UUID> rowIds, boolean approved) {
}
