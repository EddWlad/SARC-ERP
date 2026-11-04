package com.tidsec.sarc_bakend.modules.uptime.api.request;

import com.tidsec.sarc_bakend.modules.uptime.application.command.PasteUptimeImportCommand;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularReader;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Filas copiadas de la hoja, con encabezado. El tipo no se deduce del contenido. */
public record PasteUptimeImportRequest(@NotNull PeriodType declaredPeriodType,
		@NotBlank @Size(max = TabularReader.MAX_BYTES) String text) {

	public PasteUptimeImportCommand toCommand() {
		return new PasteUptimeImportCommand(declaredPeriodType, text);
	}
}
