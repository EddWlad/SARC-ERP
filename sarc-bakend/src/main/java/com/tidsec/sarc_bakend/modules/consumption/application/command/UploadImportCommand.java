package com.tidsec.sarc_bakend.modules.consumption.application.command;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;

/** Reporte original (CSV/XLSX) de una plataforma. {@code scope} distingue cuentas de una misma fuente. */
public record UploadImportCommand(SessionSource source, String scope, String fileName, byte[] content) {
}
