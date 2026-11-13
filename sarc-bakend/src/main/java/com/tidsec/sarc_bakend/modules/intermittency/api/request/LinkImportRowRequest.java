package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;

import jakarta.validation.constraints.NotNull;

/** Vincula la fila a un punto KIA elegido de forma explicita (FR-003). */
public record LinkImportRowRequest(@NotNull Long version, @NotNull UUID equipmentId) {

	public ImportCommands.LinkRow toCommand(UUID batchId, UUID rowId) {
		return new ImportCommands.LinkRow(batchId, rowId, version, equipmentId);
	}
}
