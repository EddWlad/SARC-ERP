package com.tidsec.sarc_bakend.modules.consumption.application.command;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;

/** Filas copiadas desde la hoja de la plataforma, con su fila de encabezados. */
public record PasteImportCommand(SessionSource source, String scope, String text) {
}
