package com.tidsec.sarc_bakend.modules.consumption.application.port;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionReport;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Puerto de salida: dibuja el reporte de consumo en un formato (PDF, Excel). */
public interface ConsumptionReportRenderer {

	ReportFormat format();

	byte[] render(ConsumptionReport report);
}
