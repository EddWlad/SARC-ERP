package com.tidsec.sarc_bakend.modules.intermittency.api.request;

import com.tidsec.sarc_bakend.modules.intermittency.application.command.ImportCommands;

import jakarta.validation.constraints.NotBlank;

/** Bloque pegado desde una hoja de calculo: columnas separadas por tabulador. */
public record PasteIntermittencyImportRequest(@NotBlank String text) {

	public ImportCommands.Paste toCommand() {
		return new ImportCommands.Paste(text);
	}
}
