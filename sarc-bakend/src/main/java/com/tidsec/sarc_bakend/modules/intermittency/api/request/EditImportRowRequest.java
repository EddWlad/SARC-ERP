package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Correccion de una fila en la vista previa. {@code target}: {@code date}, {@code code}, {@code comment},
 * {@code test} o {@code window}. Los instantes llevan fecha, hora y desfase completos.
 */
public record EditImportRowRequest(@NotNull Long version, @Size(max = 50) List<@Valid @NotNull Edit> edits,
		@Size(max = 50) List<@Valid @NotNull NewWindow> addWindows, @Size(max = 50) List<@NotNull Integer> removePairs) {

	public record Edit(@NotBlank String target, Integer pair, String startAt, String endAt, Boolean endsNextDay,
			@Size(max = 500) String value) {
	}

	public record NewWindow(@NotBlank String startAt, String endAt) {
	}

	public ImportCommands.EditRow toCommand(UUID batchId, UUID rowId) {
		return new ImportCommands.EditRow(batchId, rowId, version,
				edits == null ? List.of() : edits.stream().map(edit -> new ImportCommands.Edit(edit.target(),
						edit.pair(), edit.startAt(), edit.endAt(), edit.endsNextDay(), edit.value())).toList(),
				addWindows == null ? List.of() : addWindows.stream()
						.map(window -> new ImportCommands.NewWindow(window.startAt(), window.endAt())).toList(),
				removePairs);
	}
}
