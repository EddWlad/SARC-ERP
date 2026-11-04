package com.tidsec.sarc_bakend.modules.uptime.application.command;

import java.time.LocalDate;

/** Informe semanal tal como se envia por correo y el lunes de la semana que reporta. */
public record UploadUptimeImportCommand(LocalDate weekStart, String fileName, byte[] content) {
}
