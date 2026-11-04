package com.tidsec.sarc_bakend.modules.uptime.application.command;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;

/** Filas copiadas de la hoja y la ventana que la persona declara haber consultado. */
public record PasteUptimeImportCommand(PeriodType declaredPeriodType, String text) {
}
