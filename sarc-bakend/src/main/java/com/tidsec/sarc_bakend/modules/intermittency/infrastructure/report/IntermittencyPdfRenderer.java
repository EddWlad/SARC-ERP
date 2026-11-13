package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.intermittency.application.port.IntermittencyReportRenderer;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.AvailabilityEstimate;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary;
import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencySummary.OutsideDetails;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.BandShare;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.TopPoint;
import com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report.IntermittencyPdfCharts.Share;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.Kpi;
import com.tidsec.sarc_bakend.shared.report.ReportFormat;

/**
 * PDF gerencial de intermitencias (FR-038): identidad corporativa, periodo y corte, resumen ejecutivo,
 * indicadores, tendencia, Top 10, tres repartos, metodologia y lo que queda fuera de lo oficial. Con el permiso
 * operativo suma un anexo con el detalle. No incluye UID/PUK ni URL OCPP.
 */
@Component
class IntermittencyPdfRenderer implements IntermittencyReportRenderer {

	static final String TITLE = "Informe gerencial de intermitencias · Red KIA";

	private final CorporateBranding branding;

	IntermittencyPdfRenderer(CorporateBranding branding) {
		this.branding = branding;
	}

	@Override
	public ReportFormat format() {
		return ReportFormat.PDF;
	}

	@Override
	public byte[] render(IntermittencyReport report) {
		IntermittencySummary summary = report.summary();
		Figures figures = summary.figures();
		var official = figures.official();
		boolean empty = official.windows() == 0;

		CorporatePdf pdf = CorporatePdf.create(branding, TITLE, report.scope(), report.cutOff())
				.section("Resumen ejecutivo").note(executive(figures), 6);
		pdf.section("Indicadores oficiales")
				.kpis(List.of(new Kpi("Tiempo confirmado fuera de línea", IntermittencyLabels.hoursUnit(official.minutes())),
						new Kpi("Cargadores afectados", IntermittencyLabels.count(official.affectedPoints())),
						new Kpi("Ventanas completas", IntermittencyLabels.count(official.windows()))))
				.kpis(List.of(new Kpi("Días-equipo", IntermittencyLabels.count(official.pointDays())),
						new Kpi("Equipos recurrentes (3 o más días)", IntermittencyLabels.count(official.recurrentPoints())),
						new Kpi("Promedio por ventana", official.averageMinutes() == null ? "—"
								: IntermittencyLabels.decimal(official.averageMinutes(), 1) + " min")));
		if (empty) {
			pdf.note(IntermittencyLabels.NO_DATA, 4);
		}
		else {
			pdf.ensureSpace(IntermittencyPdfCharts.INTRO_SPACE).section("Tendencia diaria");
			if (figures.dailyWindowsMayExceedTotal()) {
				pdf.note("Una ventana que cruza la medianoche cuenta en cada día que toca; por eso la suma de las "
						+ "barras puede superar el total de ventanas del período.");
			}
			IntermittencyPdfCharts.trend(pdf, figures.daily());
			pdf.section("Top 10 de cargadores por tiempo fuera de línea").table(
					List.of("#", "Cargador", "Código", "Tiempo (h:mm)", "Ventanas", "Días afectados"), top(figures.top()),
					new float[] { 0.5f, 4, 2, 1.5f, 1.5f, 1.5f });
			if (figures.topExtendedByTies()) {
				pdf.note("El Top 10 incluye más de diez cargadores por empate en el décimo lugar.");
			}
			shares(pdf, summary);
		}
		methodology(pdf);
		outside(pdf, report);
		if (report.availability() != null) {
			availability(pdf, report.availability());
		}
		return pdf.finish();
	}

	/** Resumen ejecutivo con el modelo de la plantilla: horas acumuladas, sin reiniciar a las 24. */
	static String executive(Figures figures) {
		var official = figures.official();
		if (official.windows() == 0) {
			return IntermittencyLabels.NO_DATA;
		}
		StringBuilder text = new StringBuilder("Durante el período se registraron ")
				.append(IntermittencyLabels.plural(official.affectedPoints(), "cargador afectado", "cargadores afectados"))
				.append(" en ").append(IntermittencyLabels.plural(official.pointDays(), "día-equipo", "días-equipo"))
				.append(", con ").append(IntermittencyLabels.plural(official.windows(), "ventana completa", "ventanas completas"))
				.append(" y ").append(IntermittencyLabels.hours(official.minutes()))
				.append(" de tiempo fuera de línea confirmado. ")
				.append(official.recurrentPoints() == 0 ? "Ningún equipo fue recurrente (3 o más días)."
						: IntermittencyLabels.plural(official.recurrentPoints(), "equipo fue recurrente",
								"equipos fueron recurrentes") + " (3 o más días).");
		if (!figures.top().isEmpty()) {
			TopPoint first = figures.top().getFirst();
			text.append(" El cargador con más tiempo fuera de línea fue ").append(first.name()).append(" (")
					.append(IntermittencyLabels.hours(first.minutes())).append(").");
		}
		return text.toString();
	}

	private static void shares(CorporatePdf pdf, IntermittencySummary summary) {
		Figures figures = summary.figures();
		List<Share> bands = new ArrayList<>();
		for (BandShare band : figures.byDurationBand()) {
			bands.add(new Share(IntermittencyLabels.band(band.band()), band.minutes(), band.windows()));
		}
		IntermittencyPdfCharts.share(pdf, "Tiempo por duración de ventana", bands);
		IntermittencyPdfCharts.share(pdf, "Concentración del tiempo", List.of(
				new Share(figures.topExtendedByTies() ? "Top 10 (con empates)" : "Top 10",
						figures.concentration().topMinutes(), 0),
				new Share("Resto de cargadores afectados", figures.concentration().restMinutes(), 0)));
		IntermittencyPdfCharts.share(pdf, "Equipos recurrentes frente a no recurrentes", List.of(
				new Share("Recurrentes (3 o más días)", figures.recurrence().recurrentMinutes(), 0),
				new Share("No recurrentes", figures.recurrence().nonRecurrentMinutes(), 0)));
	}

	private static void methodology(CorporatePdf pdf) {
		pdf.section("Notas de metodología")
				.note("Fuente: ventanas de intermitencia de los puntos de carga KIA registradas en SARC. Solo suman las "
						+ "ventanas confirmadas; cada una aporta únicamente el tiempo que cae dentro del período, en hora de "
						+ "Ecuador, sin duplicar el evento.")
				.note("El rango del período incluye su primer y su último día; el sistema lo guarda con el fin exclusivo. "
						+ "Las duraciones se muestran en horas acumuladas y minutos (h:mm).")
				.note("Franjas de duración: menos de 5 min, 5 a 14 min, 15 a 59 min, 1 h a menos de 4 h y 4 h o más. La "
						+ "franja se decide por la duración total de la ventana, aunque cruce el límite del período.")
				.note("El Top 10 ordena por tiempo confirmado; entre iguales, por más ventanas, más días afectados y "
						+ "nombre actual. Incluye a todos los empatados con el décimo puesto. Un cargador es recurrente con "
						+ "tiempo confirmado en 3 o más días distintos.")
				.note("Las ventanas abiertas, por revisar y de prueba, y las filas pendientes de importaciones, se "
						+ "informan aparte y no suman en los indicadores. Estas cifras no sustituyen las mediciones de uptime.");
	}

	private void outside(CorporatePdf pdf, IntermittencyReport report) {
		IntermittencySummary summary = report.summary();
		var outside = summary.figures().unofficial();
		// Los mismos rotulos y el mismo total que el tablero: por revisar + en conflicto + por vincular (FR-033).
		pdf.section("Fuera de los indicadores oficiales")
				.kpis(List.of(new Kpi("Total por revisar", IntermittencyLabels.count(summary.toReview())),
						new Kpi("Ventanas por revisar (4 h o más)", IntermittencyLabels.count(outside.review())),
						new Kpi("Filas en conflicto", IntermittencyLabels.count(summary.conflict()))))
				.kpis(List.of(new Kpi("Filas por vincular", IntermittencyLabels.count(summary.pendingLink())),
						new Kpi("Eventos abiertos", IntermittencyLabels.count(outside.open().count())
								+ (outside.open().count() > 0
										? " (" + IntermittencyLabels.hoursUnit(outside.open().provisionalMinutes()) + " provisional)" : "")),
						new Kpi("Ventanas de prueba", IntermittencyLabels.count(outside.test()))))
				.note("Se conservan y se informan aparte; no suman en los totales. La duración de un evento abierto es "
						+ "provisional hasta que se cierra.");
		OutsideDetails details = summary.details();
		if (!report.operationalAllowed() || details == null) {
			return;
		}
		if (!details.windows().isEmpty()) {
			List<List<String>> rows = new ArrayList<>();
			for (var window : details.windows()) {
				rows.add(List.of(IntermittencyLabels.state(window.state()), window.pointLabel(),
						IntermittencyReports.instant(branding, window.startAt()),
						window.endAt() == null ? "Abierta" : IntermittencyReports.instant(branding, window.endAt()),
						IntermittencyLabels.hoursUnit(window.minutes()) + (window.endAt() == null ? " provisional" : "")));
			}
			pdf.section("Anexo: ventanas fuera de lo oficial").table(
					List.of("Estado", "Cargador", "Inicio", "Fin", "Tiempo en el período"), rows,
					new float[] { 1.5f, 4, 2, 2, 2 });
		}
		if (!details.pendingRows().isEmpty()) {
			List<List<String>> rows = new ArrayList<>();
			for (var row : details.pendingRows()) {
				rows.add(List.of("Fila " + row.rowNumber() + " de un lote confirmado", row.classification().name()));
			}
			pdf.section("Anexo: filas pendientes de lotes confirmados").table(List.of("Fila", "Clasificación"), rows,
					new float[] { 6, 3 });
		}
		if (details.truncated()) {
			pdf.note("Se muestran los primeros 100 de cada lista; el resto se resuelve desde la sección Revisión.");
		}
	}

	/**
	 * Apartado separado de la disponibilidad estimada de prueba (FR-046a): solo para quien tiene el permiso. No se
	 * mezcla con el Top 10 ni con el detalle por cargador y no se presenta como uptime ni como medicion de Enerlink.
	 */
	private static void availability(CorporatePdf pdf, AvailabilityEstimate result) {
		var estimate = result.estimate();
		var aggregate = estimate.aggregate();
		pdf.ensureSpace(IntermittencyPdfCharts.INTRO_SPACE).section("Disponibilidad estimada (prueba)")
				.note("Disponibilidad estimada por intermitencias (prueba). Es una estimación con los días revisados "
						+ "como cobertura: no es una medición de uptime ni de Enerlink. Origen: " + result.origin() + ".", 4)
				.kpis(List.of(new Kpi("Disponibilidad estimada (ponderada)", IntermittencyLabels.availability(aggregate.percent())),
						new Kpi("Cobertura", IntermittencyLabels.coverage(estimate.coverage().coveredDays(),
								estimate.coverage().daysInScope())),
						new Kpi("Tiempo evaluado · fuera de línea", IntermittencyLabels.hoursUnit(aggregate.evaluatedMinutes())
								+ " · " + IntermittencyLabels.hoursUnit(aggregate.offlineMinutes()))));
		if (!estimate.points().isEmpty()) {
			List<List<String>> rows = new ArrayList<>();
			for (var point : estimate.points()) {
				rows.add(List.of(point.name(), IntermittencyLabels.code(point.code()),
						IntermittencyLabels.availability(point.percent()), IntermittencyLabels.hoursUnit(point.evaluatedMinutes()),
						IntermittencyLabels.hoursUnit(point.offlineMinutes()),
						IntermittencyLabels.coverage(point.coveredDays(), estimate.coverage().daysInScope())));
			}
			pdf.section("Disponibilidad estimada por cargador (de menor a mayor)").table(
					List.of("Cargador", "Código", "Disponibilidad", "Tiempo evaluado", "Fuera de línea", "Días cubiertos"), rows,
					new float[] { 4, 2, 1.5f, 1.5f, 1.5f, 1.2f });
		}
		if (!result.exclusions().isEmpty()) {
			List<List<String>> rows = new ArrayList<>();
			for (var exclusion : result.exclusions()) {
				rows.add(List.of(exclusion.name(), IntermittencyLabels.code(exclusion.code()),
						IntermittencyLabels.exclusion(exclusion.reason())));
			}
			pdf.section("Cargadores fuera del alcance de la estimación").table(List.of("Cargador", "Código", "Motivo"), rows,
					new float[] { 4, 2, 4 });
		}
		if (!estimate.blockedDays().isEmpty()) {
			List<List<String>> rows = new ArrayList<>();
			for (var day : estimate.blockedDays()) {
				rows.add(List.of(IntermittencyLabels.DAY.format(day.day()), IntermittencyLabels.count(day.pendingRows())));
			}
			pdf.section("Días sin cubrir por filas pendientes de vincular").table(List.of("Día", "Filas por vincular"), rows,
					new float[] { 4, 3 });
		}
		pdf.section("Limitaciones de la disponibilidad estimada");
		result.limitations().forEach(pdf::note);
	}

	private static List<List<String>> top(List<TopPoint> top) {
		List<List<String>> rows = new ArrayList<>();
		int[] positions = IntermittencyReports.positions(top);
		for (int index = 0; index < top.size(); index++) {
			TopPoint point = top.get(index);
			rows.add(List.of(String.valueOf(positions[index]), point.name(), IntermittencyLabels.code(point.code()),
					IntermittencyLabels.hours(point.minutes()), IntermittencyLabels.count(point.windows()),
					IntermittencyLabels.count(point.days())));
		}
		return rows;
	}
}
