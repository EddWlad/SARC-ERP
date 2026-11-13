package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyReportRenderer;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.PointRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport.WindowRow;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideDetails;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.BandShare;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.TopPoint;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporateWorkbook;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * Excel de intermitencias (FR-039): resumen, detalle por cargador, ventanas confirmadas con su porcion dentro
 * del rango y lo que queda fuera de lo oficial. Las cifras van como numeros para poder sumarlas: la suma de la
 * hoja Ventanas es igual al tiempo confirmado de Resumen y de cada cargador.
 */
@Component
class IntermittencyExcelRenderer implements IntermittencyReportRenderer {

	static final String SUMMARY_SHEET = "Resumen";
	static final String POINTS_SHEET = "Por cargador";
	static final String WINDOWS_SHEET = "Ventanas";
	static final String OUTSIDE_SHEET = "Fuera de oficiales";
	static final String AVAILABILITY_SHEET = "Disponibilidad";
	/** Rotulo de la fila de Resumen con el tiempo confirmado, en minutos: la que se compara con el detalle. */
	static final String TOTAL_LABEL = "Tiempo confirmado fuera de línea (minutos)";

	private final CorporateBranding branding;

	IntermittencyExcelRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.XLSX;
	}

	@Override
	public byte[] render(IntermittencyReport report) {
		CorporateWorkbook book = CorporateWorkbook.create(branding, report.scope(), report.cutOff())
				.sheet(SUMMARY_SHEET, "Intermitencias KIA - resumen", List.of("Sección", "Concepto", "Valor", "Detalle"),
						summary(report))
				.sheet(POINTS_SHEET, "Tiempo fuera de línea por cargador",
						List.of("Código", "Cargador", "Días afectados", "% de días del período", "Ventanas", "Minutos",
								"Tiempo (h:mm)"),
						report.points().stream().map(IntermittencyExcelRenderer::point).toList())
				.sheet(WINDOWS_SHEET, "Ventanas confirmadas",
						List.of("Código", "Cargador", "Etiqueta del hecho", "Inicio", "Fin", "Duración total (min)",
								"Minutos dentro del rango", "Franja"),
						report.windows().stream().map(this::window).toList())
				.sheet(OUTSIDE_SHEET, "Fuera de los indicadores oficiales",
						List.of("Concepto", "Cantidad", "Minutos", "Detalle"), outside(report));
		// La disponibilidad estimada es una hoja aparte, solo para quien tiene el permiso (FR-046a).
		if (report.availability() != null) {
			book.sheet(AVAILABILITY_SHEET, "Disponibilidad estimada (prueba)",
					List.of("Sección", "Concepto", "Valor", "Detalle"), availability(report.availability()));
		}
		return book.finish();
	}

	private List<List<Object>> summary(IntermittencyReport report) {
		IntermittencySummary summary = report.summary();
		Figures figures = summary.figures();
		var official = figures.official();
		List<List<Object>> rows = new ArrayList<>();
		rows.add(List.of("Período", "Tipo", IntermittencyLabels.preset(summary.period().preset()), ""));
		rows.add(List.of("Período", "Primer día", IntermittencyLabels.DAY.format(summary.period().from()), ""));
		rows.add(List.of("Período", "Último día (incluido)",
				IntermittencyLabels.DAY.format(summary.period().to().minusDays(1)), ""));
		rows.add(List.of("Período", "Zona horaria", branding.zone().getId(), ""));
		rows.add(List.of("Período", "Fecha de corte", IntermittencyReports.instant(branding, report.cutOff()), ""));
		rows.add(List.of("Indicadores oficiales", TOTAL_LABEL, official.minutes(),
				IntermittencyLabels.hoursUnit(official.minutes())));
		rows.add(List.of("Indicadores oficiales", "Cargadores afectados", official.affectedPoints(), ""));
		rows.add(List.of("Indicadores oficiales", "Ventanas completas", official.windows(), ""));
		rows.add(List.of("Indicadores oficiales", "Días-equipo", official.pointDays(), ""));
		rows.add(List.of("Indicadores oficiales", "Equipos recurrentes (3 o más días)", official.recurrentPoints(), ""));
		rows.add(List.of("Indicadores oficiales", "Promedio por ventana (min)",
				official.averageMinutes() == null ? "" : official.averageMinutes(), ""));
		int[] positions = IntermittencyReports.positions(figures.top());
		for (int index = 0; index < figures.top().size(); index++) {
			TopPoint point = figures.top().get(index);
			rows.add(List.of("Top 10", positions[index] + ". " + point.name()
					+ (point.code() == null ? "" : " (" + point.code() + ")"), point.minutes(),
					IntermittencyLabels.plural(point.windows(), "ventana", "ventanas") + " · "
							+ IntermittencyLabels.plural(point.days(), "día", "días")));
		}
		if (figures.topExtendedByTies()) {
			rows.add(List.of("Top 10", "Incluye más de diez cargadores por empate en el décimo lugar", "", ""));
		}
		long total = official.minutes();
		for (BandShare band : figures.byDurationBand()) {
			rows.add(List.of("Tiempo por duración de ventana", IntermittencyLabels.band(band.band()), band.minutes(),
					IntermittencyLabels.plural(band.windows(), "ventana", "ventanas") + " · "
							+ IntermittencyLabels.percent(band.minutes(), total)));
		}
		if (total > 0) {
			rows.add(List.of("Concentración del tiempo",
					figures.topExtendedByTies() ? "Top 10 (con empates)" : "Top 10", figures.concentration().topMinutes(),
					IntermittencyLabels.percent(figures.concentration().topMinutes(), total)));
			rows.add(List.of("Concentración del tiempo", "Resto de cargadores afectados",
					figures.concentration().restMinutes(),
					IntermittencyLabels.percent(figures.concentration().restMinutes(), total)));
			rows.add(List.of("Equipos recurrentes", "Recurrentes (3 o más días)", figures.recurrence().recurrentMinutes(),
					IntermittencyLabels.percent(figures.recurrence().recurrentMinutes(), total)));
			rows.add(List.of("Equipos recurrentes", "No recurrentes", figures.recurrence().nonRecurrentMinutes(),
					IntermittencyLabels.percent(figures.recurrence().nonRecurrentMinutes(), total)));
		}
		return rows;
	}

	private static List<Object> point(PointRow point) {
		return List.of(IntermittencyLabels.code(point.code()), point.name(), point.days(),
				point.percentOfDays() == null ? "" : point.percentOfDays(), point.windows(), point.minutes(),
				IntermittencyLabels.hours(point.minutes()));
	}

	private List<Object> window(WindowRow window) {
		return List.of(IntermittencyLabels.code(window.code()), window.name(), window.pointLabel(),
				IntermittencyReports.instant(branding, window.startAt()),
				IntermittencyReports.instant(branding, window.endAt()), window.durationMinutes(),
				window.portionMinutes(), IntermittencyLabels.band(window.band()));
	}

	/** Mismo contenido que el apartado de disponibilidad del PDF, en filas de cuatro columnas. */
	private static List<List<Object>> availability(AvailabilityEstimate result) {
		var estimate = result.estimate();
		var aggregate = estimate.aggregate();
		List<List<Object>> rows = new ArrayList<>();
		rows.add(List.of("Disponibilidad estimada", "Rótulo", "Disponibilidad estimada por intermitencias (prueba)",
				"Estimación con los días revisados como cobertura; no es uptime ni una medición de Enerlink"));
		rows.add(List.of("Disponibilidad estimada", "Disponibilidad ponderada (%)",
				aggregate.percent() == null ? "Sin datos" : aggregate.percent(), "Ponderada por minutos"));
		rows.add(List.of("Disponibilidad estimada", "Tiempo evaluado (minutos)", aggregate.evaluatedMinutes(),
				IntermittencyLabels.hoursUnit(aggregate.evaluatedMinutes())));
		rows.add(List.of("Disponibilidad estimada", "Fuera de línea confirmado (minutos)", aggregate.offlineMinutes(),
				IntermittencyLabels.hoursUnit(aggregate.offlineMinutes())));
		rows.add(List.of("Cobertura", "Días cubiertos", estimate.coverage().coveredDays(),
				"de " + IntermittencyLabels.plural(estimate.coverage().daysInScope(), "día en alcance", "días en alcance")));
		rows.add(List.of("Origen", result.origin(), "", ""));
		for (var point : estimate.points()) {
			rows.add(List.of("Por cargador", point.name() + (point.code() == null ? "" : " (" + point.code() + ")"),
					point.percent() == null ? "Sin datos" : point.percent(),
					"Evaluado " + point.evaluatedMinutes() + " min · fuera de línea " + point.offlineMinutes() + " min · "
							+ IntermittencyLabels.coverage(point.coveredDays(), estimate.coverage().daysInScope()) + " cubiertos"));
		}
		for (var exclusion : result.exclusions()) {
			rows.add(List.of("Fuera del alcance",
					exclusion.name() + (exclusion.code() == null ? "" : " (" + exclusion.code() + ")"), "",
					IntermittencyLabels.exclusion(exclusion.reason())));
		}
		for (var day : estimate.blockedDays()) {
			rows.add(List.of("Días sin cubrir", IntermittencyLabels.DAY.format(day.day()), day.pendingRows(),
					"Filas por vincular que dejan el día sin cubrir para toda la red"));
		}
		for (String limitation : result.limitations()) {
			rows.add(List.of("Limitaciones", limitation, "", ""));
		}
		return rows;
	}

	/** Cantidades para todos; el detalle por ventana o fila solo cuando el informe lleva el anexo operativo. */
	private List<List<Object>> outside(IntermittencyReport report) {
		IntermittencySummary summary = report.summary();
		var outside = summary.figures().unofficial();
		List<List<Object>> rows = new ArrayList<>();
		rows.add(List.of("Eventos abiertos (duración provisional)", outside.open().count(),
				outside.open().provisionalMinutes(), "No suma al tiempo confirmado hasta que se cierra"));
		rows.add(List.of("Ventanas por revisar (4 h o más)", outside.review(), "", ""));
		rows.add(List.of("Ventanas de prueba", outside.test(), "", ""));
		rows.add(List.of("Filas por vincular", summary.pendingLink(), "", ""));
		rows.add(List.of("Filas en conflicto", summary.conflict(), "", ""));
		rows.add(List.of("Total por revisar", summary.toReview(), "",
				"Ventanas por revisar, filas en conflicto y filas por vincular"));
		OutsideDetails details = summary.details();
		if (report.operationalAllowed() && details != null) {
			for (var window : details.windows()) {
				rows.add(List.of("Detalle · " + IntermittencyLabels.state(window.state()), 1, window.minutes(),
						window.pointLabel() + " · " + IntermittencyReports.instant(branding, window.startAt()) + " → "
								+ (window.endAt() == null ? "abierta" : IntermittencyReports.instant(branding, window.endAt()))));
			}
			for (var row : details.pendingRows()) {
				rows.add(List.of("Detalle · Fila pendiente", 1, "",
						"Fila " + row.rowNumber() + " de un lote confirmado · " + row.classification().name()));
			}
			if (details.truncated()) {
				rows.add(List.of("Detalle acotado", "", "", "Se muestran los primeros 100 de cada lista"));
			}
		}
		return rows;
	}
}
