package com.tidsec.sarc_bakend.modules.consumption.api.request;

import com.tidsec.sarc_bakend.modules.consumption.application.command.PasteImportCommand;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Filas copiadas desde la hoja de la plataforma, incluida la fila de encabezados. */
public record PasteImportRequest(
		@NotNull SessionSource source,
		@Size(max = 100) String scope,
		@NotBlank @Size(max = TabularReader.MAX_BYTES) String text) {

	public PasteImportCommand toCommand() {
		return new PasteImportCommand(source, scope, text);
	}
}
