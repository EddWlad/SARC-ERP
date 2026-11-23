package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.application.port.ControlReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ControlReportView;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Excel de «Control de tickets»: la hoja {@code Control} con el encabezado, las mismas tres columnas y las mismas filas
 * que el PDF, y los totales al final; y la hoja {@code Criterios} con el periodo, los filtros, la zona, el corte y la
 * huella de las filas (contracts/reports.md, seccion 1).
 */
@Component
class ControlReportExcelRenderer implements ControlReportRenderer {

	private final CorporateBranding branding;

	ControlReportExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(ControlReportView report) {
		String scope = report.period() + " · " + report.filters() + " · Zona horaria: " + branding.zone().getId();
		List<List<Object>> rows = new ArrayList<>();
		if (report.lines().isEmpty()) {
			rows.add(row(ControlReportPdfRenderer.EMPTY, "", ""));
		}
		else {
			report.lines().forEach(line -> rows.add(row(line.code(), line.element(), line.state())));
			rows.add(row(report.totals(), "", ""));
		}
		List<List<Object>> criteria = List.of(row("Período", report.period()), row("Filtros", report.filters()),
				row("Zona horaria", branding.zone().getId()),
				row("Corte", branding.dateTimeFormatter().format(report.cutOff())),
				row("Huella de las filas (SHA-256)", report.rowsSha256()));
		return CorporateWorkbook.create(branding, scope, report.cutOff())
				.sheet("Control", ControlReportPdfRenderer.TITLE, ControlReportPdfRenderer.HEADERS, rows)
				.sheet("Criterios", "Criterios del informe", List.of("Criterio", "Valor"), criteria).finish();
	}

	private static List<Object> row(Object... values) {
		return List.of(values);
	}
}
