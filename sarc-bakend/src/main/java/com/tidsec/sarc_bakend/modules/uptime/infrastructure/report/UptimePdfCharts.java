package com.tidsec.sarc_bakend.modules.uptime.infrastructure.report;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeReport;
import com.tidsec.sarc_bakend.modules.uptime.application.result.UptimeView;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf.Line;

/** Tendencia de semanas oficiales. Una semana sin cifra es un hueco en la linea, no un cero. */
final class UptimePdfCharts {

	static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("dd/MM");
	private static final float LINE_HEIGHT = 130;
	/** Titulo de seccion y linea en la misma pagina. */
	static final float TREND_SPACE = LINE_HEIGHT + 60;
	private static final BigDecimal SCALE = BigDecimal.valueOf(100);

	private UptimePdfCharts() {
	}

	static void trend(CorporatePdf pdf, UptimeReport report) {
		List<UptimeView.TrendPoint> trend = report.trend();
		if (trend.stream().map(UptimeView.TrendPoint::uptimePercent).allMatch(Objects::isNull)) {
			pdf.note("Sin semanas oficiales en este alcance no se dibuja tendencia.");
			return;
		}
		pdf.line(new Line("Uptime oficial por semana (%)", LINE_HEIGHT, trend.stream().map(UptimePdfCharts::ratio).toList(),
				trend.stream().map(point -> WEEK.format(point.weekStart())).toList(), "100 %"));
	}

	private static Float ratio(UptimeView.TrendPoint point) {
		return point.uptimePercent() == null ? null
				: Float.valueOf(point.uptimePercent().divide(SCALE).floatValue());
	}
}
