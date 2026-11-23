package com.tidsec.sarc_bakend.modules.incident.application.port;

import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Puerto de salida: dibuja el reporte de gestion de incidencias en un formato (PDF, Excel). */
public interface ManagementReportRenderer {

	ReportFormat format();

	byte[] render(ManagementData data);
}
