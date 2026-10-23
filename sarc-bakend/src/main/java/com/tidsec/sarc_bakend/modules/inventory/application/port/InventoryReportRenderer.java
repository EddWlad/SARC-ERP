package com.tidsec.sarc_bakend.modules.inventory.application.port;

import com.tidsec.sarc_bakend.modules.inventory.application.result.InventoryReport;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** Puerto de salida: dibuja el reporte de inventario en un formato (PDF, Excel). */
public interface InventoryReportRenderer {

	ReportFormat format();

	byte[] render(InventoryReport report);
}
