package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeReportRenderer;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.UptimeCalculator;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.Kpi;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/** PDF de disponibilidad: oficial y provisional siempre en apartados separados y rotulados. */
@Component
class UptimePdfRenderer implements UptimeReportRenderer {

	private static final float[] CONNECTOR_WIDTHS = { 0.5f, 3.5f, 1.5f, 2.5f, 1.5f, 1.5f, 1.5f };
	private static final float[] EQUIPMENT_WIDTHS = { 4, 1.5f, 1, 1.5f, 1.5f, 1.5f };

	private final CorporateBranding branding;
	private final DateTimeFormatter dateTime;

	UptimePdfRenderer(CorporateBranding branding) {
		this.branding = branding;
		this.dateTime = branding.dateTimeFormatter();
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(UptimeReport report) {
		CorporatePdf pdf = CorporatePdf.create(branding, "Uptime y disponibilidad", report.scope(), report.cutOff());
		pdf.section("Semanas oficiales");
		if (report.official() == null) {
			pdf.note(UptimeLabels.NO_OFFICIAL);
		}
		else {
			pdf.note(range(report.official())).kpis(kpis(report.official().kpis()));
		}
		if (report.provisional() != null) {
			pdf.section("PROVISIONAL · " + UptimeLabels.of(report.provisional().periodType()))
					.note(range(report.provisional()) + ". Ventana móvil: no es una semana oficial.")
					.kpis(kpis(report.provisional().kpis()));
		}
		pdf.ensureSpace(UptimePdfCharts.TREND_SPACE).section("Tendencia de semanas oficiales");
		UptimePdfCharts.trend(pdf, report);
		pdf.section("Horas fuera de línea y con falla");
		List<List<String>> hours = hours(report);
		if (hours.isEmpty()) {
			pdf.note(UptimeLabels.NO_DATA);
		}
		else {
			pdf.table(List.of("Período", "Fuera de línea (h)", "Con falla (h)"), hours, new float[] { 3, 1.5f, 1.5f });
		}
		results(pdf, "oficial", report.official());
		results(pdf, "provisional", report.provisional());
		coverage(pdf, report);
		return pdf.section("Notas de metodología").note(UptimeLabels.FORMULA).note(UptimeLabels.EQUIPMENT_NOTE).finish();
	}

	private String range(UptimeReport.Section section) {
		return UptimeLabels.of(section.periodType()) + ": " + dateTime.format(section.from()) + " a "
				+ dateTime.format(section.to());
	}

	private static List<Kpi> kpis(UptimeCalculator.Kpis kpis) {
		return List.of(new Kpi("Uptime", UptimeLabels.percent(kpis.uptimePercent())),
				new Kpi("Fuera de línea (h)", UptimeLabels.decimal(kpis.offlineHours())),
				new Kpi("Con falla (h)", UptimeLabels.decimal(kpis.faultHours())),
				new Kpi("Equipos evaluados", String.valueOf(kpis.equipmentEvaluated())),
				new Kpi("Equipos sin datos", String.valueOf(kpis.equipmentWithoutData())));
	}

	private static List<List<String>> hours(UptimeReport report) {
		List<List<String>> rows = new ArrayList<>();
		if (report.official() != null) {
			rows.add(hours("Oficial", report.official().kpis()));
		}
		if (report.provisional() != null) {
			rows.add(hours("PROVISIONAL", report.provisional().kpis()));
		}
		return rows;
	}

	private static List<String> hours(String label, UptimeCalculator.Kpis kpis) {
		return List.of(label, UptimeLabels.decimal(kpis.offlineHours()), UptimeLabels.decimal(kpis.faultHours()));
	}

	private static void results(CorporatePdf pdf, String label, UptimeReport.Section section) {
		if (section == null) {
			return;
		}
		pdf.section("Resultado por conector y menor uptime (" + label + ")")
				.table(List.of("#", "Equipo", "Código", "Conector", "Uptime", "Fuera de línea (h)", "Con falla (h)"),
						section.connectors().stream().map(UptimePdfRenderer::connector).toList(), CONNECTOR_WIDTHS)
				.section("Promedio de conectores por equipo (" + label + ")")
				.table(List.of("Equipo", "Código", "Red", "Uptime", "Conectores", "Fuera de línea (h)"),
						section.equipment().stream().map(UptimePdfRenderer::equipment).toList(), EQUIPMENT_WIDTHS);
	}

	private static List<String> connector(UptimeCalculator.ConnectorResult item) {
		return List.of(String.valueOf(item.position()), item.connector().equipmentName(),
				UptimeLabels.code(item.connector()), UptimeLabels.connector(item.connector()),
				UptimeLabels.percent(item.uptimePercent()), UptimeLabels.hours(item.offlineMinutes()),
				UptimeLabels.hours(item.faultMinutes()));
	}

	private static List<String> equipment(UptimeCalculator.EquipmentResult item) {
		return List.of(item.sample().equipmentName(), UptimeLabels.code(item.sample()), item.sample().network().name(),
				UptimeLabels.percent(item.uptimePercent()), item.connectorsIncluded() + " de " + item.connectorsInScope(),
				UptimeLabels.hours(item.offlineMinutes()));
	}

	private static void coverage(CorporatePdf pdf, UptimeReport report) {
		UptimeCalculator.Coverage coverage = report.coverage();
		pdf.section("Cobertura y exclusiones")
				.kpis(List.of(new Kpi("Cobertura", UptimeLabels.percent(coverage.percent())),
						new Kpi("Incluidas", String.valueOf(coverage.included())),
						new Kpi("Evaluables", String.valueOf(coverage.denominator())),
						new Kpi("Pendientes de vincular (sin atribuir)", String.valueOf(report.pendingLink()))))
				.table(List.of("Estado", "Cantidad"),
						UptimeLabels.STATES.stream().map(state -> List.of(UptimeLabels.of(state),
								String.valueOf(coverage.byState().getOrDefault(state, 0)))).toList(),
						new float[] { 3, 1 });
		if (!report.exclusions().isEmpty()) {
			pdf.table(List.of("Equipo", "Código", "Conector", "Estado"),
					report.exclusions().stream().map(item -> List.of(item.connector().equipmentName(),
							UptimeLabels.code(item.connector()), UptimeLabels.connector(item.connector()),
							UptimeLabels.of(item.state()))).toList(),
					new float[] { 4, 1.5f, 2.5f, 2 });
		}
	}
}
