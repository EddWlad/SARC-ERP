package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.incident.application.port.ControlReportRenderer;
import com.tidsec.sarc_bakend.modules.incident.application.result.ControlReportView;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * PDF A4 vertical de «Control de tickets» (contracts/reports.md, seccion 1): logos, titulo, subtitulo, banda «REGISTRO
 * OPERATIVO», metadatos, la tabla de tres columnas, el pie con totales y «Pagina x de y». El estado va como texto y con
 * un relleno suave en los abiertos, para que el color no sea el unico indicador.
 */
@Component
class ControlReportPdfRenderer implements ControlReportRenderer {

	static final String TITLE = "CONTROL DE TICKETS";
	static final String SUBTITLE = "Electromovilidad | Puntos de carga y electrolineras";
	static final String BAND = "REGISTRO OPERATIVO";
	static final String EMPTY = "Sin tickets en el período";
	static final List<String> HEADERS = List.of("NÚMERO DE TICKET", "CARGADOR O ELECTROLINERA", "ESTADO DEL TICKET");

	private final CorporateBranding branding;

	ControlReportPdfRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(ControlReportView report) {
		String scope = report.period() + " · " + report.filters() + " · Zona horaria: " + branding.zone().getId();
		CorporatePdf pdf = CorporatePdf.create(branding, TITLE, scope, report.cutOff(),
				new CorporatePdf.Layout(true, SUBTITLE, BAND, true));
		if (report.lines().isEmpty()) {
			pdf.note(EMPTY);
		}
		else {
			pdf.statusTable(HEADERS,
					report.lines().stream().map(line -> List.of(line.code(), line.element(), line.state())).toList(),
					new float[] { 2f, 6f, 2f }, 2, Set.of(IncidentLabels.OPEN));
			pdf.note(report.totals());
		}
		return pdf.finish();
	}
}
