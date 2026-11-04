package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.util.List;
import java.util.UUID;

/** Incluye u omite filas; incluir un conflicto exige justificacion. */
public record UptimeRowDecisionCommand(UUID batchId, List<UUID> rowIds, boolean approved, String justification) {
}
