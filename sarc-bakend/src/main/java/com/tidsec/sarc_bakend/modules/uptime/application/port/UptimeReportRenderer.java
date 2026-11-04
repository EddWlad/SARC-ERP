package com.tidsec.sarc_bakend.modules.uptime.application.port;

import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Convierte un reporte de uptime ya calculado en un documento. */
public interface UptimeReportRenderer {

	ReportFormat format();

	byte[] render(UptimeReport report);
}
