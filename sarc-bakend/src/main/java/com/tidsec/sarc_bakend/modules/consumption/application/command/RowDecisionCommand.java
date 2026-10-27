package com.tidsec.sarc_bakend.modules.consumption.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Decision del analista sobre filas de la vista previa. Un valor nulo deja esa decision como estaba;
 * asi la misma operacion sirve para una fila o para varias.
 */
public record RowDecisionCommand(UUID batchId, List<UUID> rowIds, Boolean test, Boolean approved,
		Boolean reassign) {
}
