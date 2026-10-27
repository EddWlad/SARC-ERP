package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.ConsumptionReportRenderer;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionReport;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.Kpi;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * PDF gerencial de consumo (PRD RF-003.15): logos, periodo, resumen, excluidos aparte, Top 5,
 * ranking por equipo y tendencia diaria, con notas de metodologia.
 */
@Component
class ConsumptionPdfRenderer implements ConsumptionReportRenderer {

	private final CorporateBranding branding;

	ConsumptionPdfRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(ConsumptionReport report) {
		ConsumptionSummary summary = report.summary();
		CorporatePdf pdf = CorporatePdf.create(branding, "Consumo de energía", report.scope(), report.cutOff())
				.section("Resumen")
				.kpis(List.of(
						new Kpi("Energía (kWh)", ConsumptionLabels.kwh(summary.official().energyKwh())),
						new Kpi("Sesiones", ConsumptionLabels.count(summary.official().sessions())),
						new Kpi("Equipos con actividad", ConsumptionLabels.count(summary.official().equipment())),
						new Kpi("kWh por sesión", ConsumptionLabels.kwh(summary.averageKwhPerSession()))))
				.section("Fuera de los indicadores oficiales")
				.kpis(List.of(
						new Kpi("Pendientes de vincular", ConsumptionLabels.count(summary.pendingLink().sessions())
								+ " (" + ConsumptionLabels.kwh(summary.pendingLink().energyKwh()) + " kWh)"),
						new Kpi("Por aprobar (fuente no preferente)",
								ConsumptionLabels.count(summary.awaitingApproval().sessions()) + " ("
										+ ConsumptionLabels.kwh(summary.awaitingApproval().energyKwh()) + " kWh)"),
						new Kpi("Pruebas", ConsumptionLabels.count(summary.test().sessions()) + " ("
								+ ConsumptionLabels.kwh(summary.test().energyKwh()) + " kWh)")))
				.section("Top 5 equipos por energía")
				.table(List.of("#", "Equipo", "Código", "Red", "Sesiones", "kWh"), ranked(summary.top()),
						new float[] { 0.5f, 4, 2, 1.5f, 1.5f, 1.5f })
				.section("Distribución de la energía")
				.table(List.of("Dimensión", "Categoría", "Sesiones", "kWh", "Participación"),
						distribution(summary), new float[] { 1.5f, 3, 1.5f, 1.5f, 1.5f })
				.section("Tendencia diaria")
				.table(List.of("Fecha", "Sesiones", "kWh"), daily(summary.daily()), new float[] { 2, 1.5f, 1.5f });
		if (report.ranking().size() > summary.top().size()) {
			pdf.section("Ranking por equipo (" + report.ranking().size() + ")")
					.table(List.of("#", "Equipo", "Código", "Red", "Sesiones", "kWh"), ranked(report.ranking()),
							new float[] { 0.5f, 4, 2, 1.5f, 1.5f, 1.5f });
		}
		return pdf.section("Notas de metodología")
				.note("Fuente: sesiones importadas de Enerlink y Wallbox en SARC (RF-003). Cada sesión se cuenta "
						+ "una vez, en el día de su inicio (hora de Ecuador), aunque termine al día siguiente.")
				.note("Solo suman las sesiones validadas. Las pendientes de vincular y las de prueba se informan "
						+ "aparte y no se incluyen en los totales.")
				.note("La red y el tipo de cada sesión provienen del equipo del inventario, no del archivo de "
						+ "origen. Los equipos con actividad se cuentan por su identificador interno.")
				.finish();
	}

	private static List<List<String>> ranked(List<RankedEquipment> ranking) {
		List<List<String>> rows = new ArrayList<>();
		for (int index = 0; index < ranking.size(); index++) {
			RankedEquipment item = ranking.get(index);
			rows.add(List.of(String.valueOf(index + 1), ConsumptionLabels.name(item.equipment()),
					ConsumptionLabels.code(item.equipment()), ConsumptionLabels.network(item.equipment()),
					ConsumptionLabels.count(item.sessions()), ConsumptionLabels.kwh(item.energyKwh())));
		}
		return rows;
	}

	private static List<List<String>> distribution(ConsumptionSummary summary) {
		return ConsumptionLabels.distribution(summary).stream().map(share -> List.of(share.dimension(),
				share.category(), ConsumptionLabels.count(share.sessions()), ConsumptionLabels.kwh(share.energyKwh()),
				ConsumptionLabels.percent(share.energyKwh(), summary.official().energyKwh()))).toList();
	}

	private static List<List<String>> daily(List<DailyEnergy> days) {
		return days.stream().map(day -> List.of(ConsumptionLabels.DAY.format(day.date()),
				ConsumptionLabels.count(day.sessions()), ConsumptionLabels.kwh(day.energyKwh()))).toList();
	}
}
