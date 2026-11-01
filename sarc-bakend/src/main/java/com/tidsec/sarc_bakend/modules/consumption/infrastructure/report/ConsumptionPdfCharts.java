package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionChartData;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionChartData.Point;
import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionChartData.Series;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf.Bar;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf.Line;

/**
 * Dibuja las seis representaciones del informe. Las tendencias se parten en tramos con la misma
 * escala y las barras son filas de tabla, asi que ningun dia ni empatado se pierde al paginar.
 */
final class ConsumptionPdfCharts {

	/** Dias por tramo: legibles en el ancho de un A4 horizontal, con cada punto distinguible. */
	static final int DAYS_PER_SEGMENT = 62;
	private static final float LINE_HEIGHT = 130;
	/** Titulo de seccion, nota y primera tendencia en la misma pagina. */
	static final float INTRO_SPACE = LINE_HEIGHT + 90;

	private ConsumptionPdfCharts() {
	}

	static void render(CorporatePdf pdf, ConsumptionChartData charts) {
		line(pdf, charts.dailyEnergy());
		line(pdf, charts.dailySessions());
		bars(pdf, charts.top());
		bars(pdf, charts.byNetwork());
		bars(pdf, charts.bySource());
		bars(pdf, charts.byType());
	}

	private static void line(CorporatePdf pdf, Series series) {
		String title = title(series);
		if (!series.hasValues()) {
			pdf.section(title).note(ConsumptionLabels.NO_DATA);
			return;
		}
		BigDecimal max = max(series.points());
		List<List<Point>> segments = segments(series.points());
		for (int index = 0; index < segments.size(); index++) {
			List<Point> segment = segments.get(index);
			String continuation = segments.size() == 1 ? ""
					: " · tramo " + (index + 1) + " de " + segments.size() + ": " + segment.getFirst().label() + " a "
							+ segment.getLast().label();
			pdf.line(new Line(title + continuation, LINE_HEIGHT, ratios(segment, max),
					segment.stream().map(Point::label).toList(), scaleLabel(series, max)));
		}
	}

	private static void bars(CorporatePdf pdf, Series series) {
		pdf.section(title(series));
		if (series.points().isEmpty()) {
			pdf.note(ConsumptionLabels.NO_DATA);
			return;
		}
		BigDecimal max = max(series.points());
		pdf.bars(List.of("Categoría", "Escala 0 a " + scaleLabel(series, max) + " " + series.unit(), series.unit()),
				series.points().stream().map(point -> new Bar(point.label(), ratio(point, max), value(point))).toList());
	}

	private static String title(Series series) {
		return series.title() + " (" + series.unit() + ")";
	}

	static List<List<Point>> segments(List<Point> points) {
		List<List<Point>> segments = new ArrayList<>();
		for (int start = 0; start < points.size(); start += DAYS_PER_SEGMENT) {
			segments.add(points.subList(start, Math.min(start + DAYS_PER_SEGMENT, points.size())));
		}
		return segments;
	}

	private static List<Float> ratios(List<Point> points, BigDecimal max) {
		return points.stream().map(point -> ratio(point, max)).toList();
	}

	private static Float ratio(Point point, BigDecimal max) {
		if (point.unknown()) {
			return null;
		}
		return max.signum() == 0 ? 0f : point.value().divide(max, MathContext.DECIMAL64).floatValue();
	}

	private static BigDecimal max(List<Point> points) {
		return points.stream().filter(point -> !point.unknown()).map(Point::value).max(BigDecimal::compareTo)
				.orElse(BigDecimal.ZERO);
	}

	private static String scaleLabel(Series series, BigDecimal max) {
		return "kWh".equals(series.unit()) ? ConsumptionLabels.kwh(max) : ConsumptionLabels.count(max.longValue());
	}

	/** Ninguna sesion y ninguna lectura se dicen con palabras: una barra vacia pareceria un cero. */
	private static String value(Point point) {
		if (point.withoutSessions()) {
			return "Sin sesiones";
		}
		return ConsumptionLabels.kwh(point.value());
	}
}
