package com.tidsec.sarc_bakend.modules.incident.application.port;

import com.tidsec.sarc_bakend.modules.incident.application.result.ControlReportView;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Puerto de salida: dibuja el informe «Control de tickets» en un formato (PDF, Excel). */
public interface ControlReportRenderer {

	ReportFormat format();

	byte[] render(ControlReportView report);
}
