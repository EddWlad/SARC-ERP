package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.DailyPoint;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf.Bar;
import com.tidsec.sarc_bakend.shared.report.CorporatePdf.Line;

/**
 * Dibuja las representaciones del informe con la paleta corporativa: tendencia en horas como linea, ventanas
 * por dia como barras y cada reparto como barras de participacion con su leyenda en texto. Las tendencias se
 * parten en tramos con la misma escala y las barras son filas de tabla, asi que ningun dia ni categoria se
 * pierde al paginar.
 */
final class IntermittencyPdfCharts {

	/** Dias por tramo: legibles en el ancho de un A4 horizontal, con cada punto distinguible. */
	static final int DAYS_PER_SEGMENT = 62;

	private static final float LINE_HEIGHT = 130;

	/** Titulo de seccion, nota y primera tendencia en la misma pagina. */
	static final float INTRO_SPACE = LINE_HEIGHT + 90;

	/** Una categoria de un reparto. */
	record Share(String label, long minutes, long windows) {
	}

	private IntermittencyPdfCharts() {
	}

	/** Linea de horas por dia y barras de ventanas por dia. */
	static void trend(CorporatePdf pdf, List<DailyPoint> daily) {
		long maxMinutes = daily.stream().mapToLong(DailyPoint::minutes).max().orElse(0);
		for (int start = 0; start < daily.size(); start += DAYS_PER_SEGMENT) {
			List<DailyPoint> segment = daily.subList(start, Math.min(start + DAYS_PER_SEGMENT, daily.size()));
			int segments = (daily.size() + DAYS_PER_SEGMENT - 1) / DAYS_PER_SEGMENT;
			String continuation = segments == 1 ? ""
					: " · tramo " + (start / DAYS_PER_SEGMENT + 1) + " de " + segments + ": "
							+ IntermittencyLabels.DAY.format(segment.getFirst().day()) + " a "
							+ IntermittencyLabels.DAY.format(segment.getLast().day());
			List<Float> ratios = new ArrayList<>();
			for (DailyPoint point : segment) {
				ratios.add(maxMinutes == 0 ? 0f : (float) point.minutes() / maxMinutes);
			}
			pdfLine(pdf, "Tiempo confirmado por día (h:mm)" + continuation, ratios,
					segment.stream().map(point -> point.day().getDayOfMonth() + "/" + point.day().getMonthValue()).toList(),
					IntermittencyLabels.hoursUnit(maxMinutes));
		}
		long maxWindows = daily.stream().mapToLong(DailyPoint::windows).max().orElse(0);
		pdf.section("Ventanas por día (cantidad)");
		pdf.bars(List.of("Día", "Escala 0 a " + IntermittencyLabels.count(maxWindows) + " ventanas", "Ventanas"),
				daily.stream().map(point -> new Bar(IntermittencyLabels.DAY.format(point.day()),
						maxWindows == 0 ? 0f : (float) point.windows() / maxWindows,
						IntermittencyLabels.count(point.windows()))).toList());
	}

	/** Un reparto como barras de participacion: nombre, barra, tiempo y porcentaje, aunque el color falte. */
	static void share(CorporatePdf pdf, String title, List<Share> shares) {
		pdf.section(title);
		long total = shares.stream().mapToLong(Share::minutes).sum();
		pdf.bars(List.of("Categoría", "Participación (0 a 100 %)", "Tiempo · porcentaje"),
				shares.stream().map(share -> new Bar(share.label(),
						total == 0 ? 0f : (float) share.minutes() / total,
						IntermittencyLabels.hoursUnit(share.minutes()) + " · "
								+ IntermittencyLabels.percent(share.minutes(), total)
								+ (share.windows() > 0 ? " · " + IntermittencyLabels.plural(share.windows(), "ventana", "ventanas") : "")))
						.toList());
	}

	private static void pdfLine(CorporatePdf pdf, String title, List<Float> ratios, List<String> labels, String scale) {
		pdf.line(new Line(title, LINE_HEIGHT, ratios, labels, scale));
	}
}
