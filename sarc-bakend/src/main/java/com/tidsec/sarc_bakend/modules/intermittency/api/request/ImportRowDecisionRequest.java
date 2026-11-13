package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decision sobre una fila y sobre cada una de sus ventanas (FR-027 a FR-029). */
public record ImportRowDecisionRequest(@NotNull Long version, @NotNull RowDecision decision,
		@Size(max = 500) String skipReason, Boolean acknowledgeTotal,
		@Size(max = 50) List<@Valid @NotNull WindowDecision> windows) {

	/** {@code resolution} nulo retira la decision anterior del par. */
	public record WindowDecision(@NotNull Integer pair, WindowResolution resolution,
			@Size(max = 500) String justification) {
	}

	public ImportCommands.DecideRow toCommand(UUID batchId, UUID rowId) {
		return new ImportCommands.DecideRow(batchId, rowId, version, decision, skipReason,
				Boolean.TRUE.equals(acknowledgeTotal), windows == null ? List.of() : windows.stream()
						.map(window -> new ImportCommands.WindowDecision(window.pair(), window.resolution(),
								window.justification())).toList());
	}
}
