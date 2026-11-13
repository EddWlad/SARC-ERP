package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.api.request.EditImportRowRequest.Edit;
import com.tidsec.sarc_bakend.modules.intermittency.api.request.ImportRowDecisionRequest.WindowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Resuelve una fila pendiente de un lote confirmado: vincula, corrige, decide cada ventana y aplica u omite. */
public record ResolvePendingRowRequest(@NotNull Long version, UUID equipmentId,
		@Size(max = 50) List<@Valid @NotNull Edit> edits, @NotNull RowDecision decision,
		@Size(max = 500) String skipReason, Boolean acknowledgeTotal,
		@Size(max = 50) List<@Valid @NotNull WindowDecision> windows) {

	public ImportCommands.ResolvePending toCommand(UUID batchId, UUID rowId) {
		return new ImportCommands.ResolvePending(batchId, rowId, version, equipmentId,
				edits == null ? List.of() : edits.stream().map(edit -> new ImportCommands.Edit(edit.target(),
						edit.pair(), edit.startAt(), edit.endAt(), edit.endsNextDay(), edit.value())).toList(),
				decision, skipReason, Boolean.TRUE.equals(acknowledgeTotal), windows == null ? List.of()
						: windows.stream().map(window -> new ImportCommands.WindowDecision(window.pair(),
								window.resolution(), window.justification())).toList());
	}
}
