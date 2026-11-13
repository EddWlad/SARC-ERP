package com.tidsec.sarc_bakend.modules.intermittency.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.application.result.IntermittencyReport;

/** Excel de intermitencias: hojas, cifras como numeros y la comprobacion de que el detalle suma lo mismo. */
class IntermittencyExcelRendererTest {

	private final IntermittencyExcelRenderer renderer = new IntermittencyExcelRenderer(IntermittencyReportSamples.branding());

	private XSSFWorkbook workbook(IntermittencyReport report) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(renderer.render(report)));
	}

	/** Filas de datos de una hoja: lo que esta debajo de la fila de encabezados, que empieza con {@code firstHeader}. */
	static List<List<Object>> rows(Sheet sheet, String firstHeader) {
		List<List<Object>> rows = new ArrayList<>();
		boolean inTable = false;
		for (Row row : sheet) {
			Cell first = row.getCell(0);
			if (!inTable) {
				inTable = first != null && first.getCellType() == CellType.STRING
						&& firstHeader.equals(first.getStringCellValue());
				continue;
			}
			List<Object> values = new ArrayList<>();
			for (int column = 0; column < row.getLastCellNum(); column++) {
				Cell cell = row.getCell(column);
				values.add(cell == null ? null : cell.getCellType() == CellType.NUMERIC ? (Object) cell.getNumericCellValue()
						: cell.getStringCellValue());
			}
			rows.add(values);
		}
		return rows;
	}

	private static double sum(List<List<Object>> rows, int column) {
		return rows.stream().mapToDouble(row -> ((Number) row.get(column)).doubleValue()).sum();
	}

	@Test
	void hasTheFourSheetsOfTheContract() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.report(false))) {
			List<String> names = new ArrayList<>();
			book.forEach(sheet -> names.add(sheet.getSheetName()));

			assertThat(names).containsExactly("Resumen", "Por cargador", "Ventanas", "Fuera de oficiales");
		}
	}

	@Test
	void theWindowsSheetAddsUpToTheSummaryAndToEachPoint() throws Exception {
		IntermittencyReport report = IntermittencyReportSamples.report(false);
		try (var book = workbook(report)) {
			var windows = rows(book.getSheet("Ventanas"), "Código");
			var points = rows(book.getSheet("Por cargador"), "Código");
			var summary = rows(book.getSheet("Resumen"), "Sección");
			double total = summary.stream().filter(row -> IntermittencyExcelRenderer.TOTAL_LABEL.equals(row.get(1)))
					.mapToDouble(row -> ((Number) row.get(2)).doubleValue()).sum();

			assertThat(total).isEqualTo(report.summary().figures().official().minutes());
			// La suma de «minutos dentro del rango» (columna 6) es el total de Resumen...
			assertThat(sum(windows, 6)).isEqualTo(total);
			assertThat(windows).hasSize(report.summary().figures().official().windows());
			// ...y el de cada cargador (columna 5 de Por cargador).
			assertThat(sum(points, 5)).isEqualTo(total);
			for (var point : points) {
				double minutes = windows.stream().filter(row -> row.get(0).equals(point.get(0)))
						.mapToDouble(row -> ((Number) row.get(6)).doubleValue()).sum();
				assertThat(minutes).isEqualTo(((Number) point.get(5)).doubleValue());
			}
		}
	}

	@Test
	void theWindowsSheetHasOnlyConfirmedWindowsWithTheirTotalDurationBandAndPortion() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.report(true))) {
			var windows = rows(book.getSheet("Ventanas"), "Código");

			// Las de revision, abierta y prueba del ejemplo no estan: solo las 7 confirmadas.
			assertThat(windows).hasSize(7);
			// Varias ventanas empiezan el mismo instante: se busca una de 600 minutos del primer cargador.
			var first = windows.stream().filter(row -> ((Number) row.get(5)).intValue() == 600).findFirst().orElseThrow();
			assertThat(first.get(1)).isEqualTo("QA Punto A");
			assertThat(first.get(2)).isEqualTo("QA Punto A (900001)");
			assertThat(((Number) first.get(5)).intValue()).isEqualTo(600);
			assertThat(((Number) first.get(6)).intValue()).isEqualTo(600);
			assertThat(first.get(7)).isEqualTo("4 h o más");
		}
	}

	@Test
	void thePointSheetHasTheShareOfTheCalendarDaysOfThePeriodAndNumbersNotText() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.report(false))) {
			var points = rows(book.getSheet("Por cargador"), "Código");

			var first = points.getFirst();
			assertThat(first.get(1)).isEqualTo("QA Punto A");
			// 4 dias afectados de 15 dias calendario: 26,7 %.
			assertThat(((Number) first.get(2)).intValue()).isEqualTo(4);
			assertThat(((Number) first.get(3)).doubleValue()).isEqualTo(26.7);
			assertThat(((Number) first.get(5)).intValue()).isEqualTo(1632);
			assertThat(first.get(6)).isEqualTo("27:12");
			assertThat(points.get(1).get(1)).isEqualTo("QA Punto B");
		}
	}

	@Test
	void theSummaryHasThePeriodTheCutOffTheTopAndThePiesAsTables() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.withTies(false))) {
			var summary = rows(book.getSheet("Resumen"), "Sección");
			List<Object> sections = summary.stream().map(row -> row.get(0)).toList();

			assertThat(sections).contains("Período", "Indicadores oficiales", "Top 10", "Tiempo por duración de ventana",
					"Concentración del tiempo", "Equipos recurrentes");
			assertThat(summary.stream().filter(row -> "Zona horaria".equals(row.get(1))).findFirst().orElseThrow().get(2))
					.isEqualTo("America/Guayaquil");
			assertThat(summary.stream().anyMatch(row -> "Fecha de corte".equals(row.get(1)))).isTrue();
			// Doce filas del Top y la nota del empate.
			assertThat(summary.stream().filter(row -> "Top 10".equals(row.get(0))).count()).isEqualTo(13);
			assertThat(summary.stream().anyMatch(row -> String.valueOf(row.get(1)).startsWith("Incluye más de diez")))
					.isTrue();
			var bands = summary.stream().filter(row -> "Tiempo por duración de ventana".equals(row.get(0))).toList();
			assertThat(bands).hasSize(5);
			double official = summary.stream().filter(row -> IntermittencyExcelRenderer.TOTAL_LABEL.equals(row.get(1)))
					.mapToDouble(row -> ((Number) row.get(2)).doubleValue()).sum();
			assertThat(sum(bands, 2)).isEqualTo(official);
		}
	}

	@Test
	void outsideOfficialHasOnlyQuantitiesForWhoIsNotAnEditorAndTheDetailForWhoIs() throws Exception {
		try (var readOnly = workbook(IntermittencyReportSamples.report(false));
				var editor = workbook(IntermittencyReportSamples.report(true))) {
			var quantities = rows(readOnly.getSheet("Fuera de oficiales"), "Concepto");
			var withDetail = rows(editor.getSheet("Fuera de oficiales"), "Concepto");

			assertThat(quantities).hasSize(6);
			assertThat(quantities.getFirst().get(0)).isEqualTo("Eventos abiertos (duración provisional)");
			assertThat(((Number) quantities.getFirst().get(1)).intValue()).isEqualTo(1);
			assertThat(quantities.stream().map(row -> String.valueOf(row.get(0))).noneMatch(text -> text.startsWith("Detalle")))
					.isTrue();
			assertThat(withDetail.size()).isGreaterThan(6);
			// El total es el mismo del tablero: por revisar + en conflicto + por vincular.
			var total = quantities.stream().filter(row -> "Total por revisar".equals(row.get(0))).findFirst().orElseThrow();
			assertThat(((Number) total.get(1)).intValue())
					.isEqualTo(IntermittencyReportSamples.report(false).summary().toReview());
			assertThat(withDetail.stream().map(row -> String.valueOf(row.get(0))))
					.anyMatch(text -> text.startsWith("Detalle · Abierta"))
					.anyMatch(text -> text.startsWith("Detalle · Por revisar"))
					.anyMatch(text -> text.startsWith("Detalle · Fila pendiente"));
		}
	}

	@Test
	void anEmptyPeriodStillHasEverySheetWithZeroTotals() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.empty(false))) {
			var summary = rows(book.getSheet("Resumen"), "Sección");

			assertThat(rows(book.getSheet("Ventanas"), "Código")).isEmpty();
			assertThat(rows(book.getSheet("Por cargador"), "Código")).isEmpty();
			assertThat(summary.stream().filter(row -> IntermittencyExcelRenderer.TOTAL_LABEL.equals(row.get(1)))
					.findFirst().orElseThrow().get(2)).isEqualTo(0.0);
		}
	}

	@Test
	void neverContainsUidPukOrAnOcppUrl() throws Exception {
		try (var book = workbook(IntermittencyReportSamples.report(true))) {
			StringBuilder all = new StringBuilder();
			book.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> all.append(cell).append(' '))));

			assertThat(all.toString().toLowerCase()).doesNotContainPattern("\\buid\\b").doesNotContainPattern("\\bpuk\\b")
					.doesNotContain("ocpp").doesNotContain("ws://");
		}
	}

	@Test
	void theAvailabilitySheetExistsOnlyForTheReportOfWhoHasThePermissionAndKeepsItApartFromTheOtherSheets()
			throws Exception {
		try (var without = workbook(IntermittencyReportSamples.report(true));
				var with = workbook(IntermittencyReportSamples.withAvailability(true))) {
			List<String> withoutNames = new ArrayList<>();
			without.forEach(sheet -> withoutNames.add(sheet.getSheetName()));
			List<String> withNames = new ArrayList<>();
			with.forEach(sheet -> withNames.add(sheet.getSheetName()));
			assertThat(withoutNames).doesNotContain("Disponibilidad");
			assertThat(withNames).containsExactly("Resumen", "Por cargador", "Ventanas", "Fuera de oficiales", "Disponibilidad");

			var rows = rows(with.getSheet("Disponibilidad"), "Sección");
			assertThat(rows.stream().map(row -> String.valueOf(row.get(1)))).contains("Disponibilidad ponderada (%)",
					"QA Punto A (900001)", "QA Punto Desconectado (900009)", "Registro manual de intermitencias (Kibana)")
					.anyMatch(text -> text.startsWith("No observa Ocupado"));
			var weighted = rows.stream().filter(row -> "Disponibilidad ponderada (%)".equals(row.get(1))).findFirst().orElseThrow();
			assertThat(((Number) weighted.get(2)).doubleValue()).isEqualTo(99.5);
			var noData = rows.stream().filter(row -> "QA Punto Sin Codigo".equals(row.get(1))).findFirst().orElseThrow();
			assertThat(noData.get(2)).isEqualTo("Sin datos");
			// Ni el Top 10 ni el detalle por cargador de las otras hojas llevan disponibilidad.
			assertThat(rows(with.getSheet("Por cargador"), "Código").toString()).doesNotContain("Disponibilidad");
			assertThat(rows(with.getSheet("Resumen"), "Sección").toString()).doesNotContain("Disponibilidad estimada");
		}
	}
}
