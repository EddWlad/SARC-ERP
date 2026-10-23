package com.tidsec.sarc_bakend.shared.report;

/** Archivo de reporte generado, listo para descargar. */
public record ReportFile(String filename, ReportFormat format, byte[] content) {
}
