package com.tidsec.sarc_bakend.modules.inventory.api.request;

import com.tidsec.sarc_bakend.modules.inventory.application.command.PasteEquipmentImportCommand;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Filas copiadas desde la plantilla, incluida la fila de encabezados. */
public record PasteEquipmentImportRequest(@NotBlank @Size(max = TabularReader.MAX_BYTES) String text) {

	public PasteEquipmentImportCommand toCommand() {
		return new PasteEquipmentImportCommand(text);
	}
}
