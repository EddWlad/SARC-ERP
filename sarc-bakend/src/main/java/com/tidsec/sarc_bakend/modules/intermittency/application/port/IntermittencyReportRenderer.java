package com.tidsec.sarc_bakend.modules.intermittency.application.port;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Puerto de salida: dibuja el informe de intermitencias en un formato (PDF, Excel). */
public interface IntermittencyReportRenderer {

	ReportFormat format();

	byte[] render(IntermittencyReport report);
}
