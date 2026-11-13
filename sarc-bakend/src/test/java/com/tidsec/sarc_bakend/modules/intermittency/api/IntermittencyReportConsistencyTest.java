package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

import jakarta.servlet.http.Cookie;

/** SC-006: con iguales filtros, el dashboard, el PDF y el Excel dicen las mismas cifras. */
class IntermittencyReportConsistencyTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;
	private static final UUID OTHER = Feature008Fixtures.FREE;
	private static final String OCTOBER = "preset=CUSTOM&from=2026-10-01&to=2026-11-01";

	private void seed() {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		// Cruza la medianoche: 10 minutos el dia 14 y 20 el dia 15.
		insertWindow(POINT, at("2026-10-14", "23:50"), at("2026-10-15", "00:20"), "CONFIRMED");
		// Larga, ya revisada: 5 horas, franja de 4 h o mas.
		insertWindow(OTHER, at("2026-10-07", "08:00"), at("2026-10-07", "13:00"), "CONFIRMED");
		insertWindow(OTHER, at("2026-10-08", "08:00"), at("2026-10-08", "08:03"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-09", "08:00"), at("2026-10-09", "13:00"), "REVIEW");
		insertWindow(POINT, at("2026-10-10", "08:00"), at("2026-10-10", "09:00"), "TEST");
		insertWindow(POINT, at("2026-10-30", "08:00"), null, "OPEN");
	}

	private byte[] download(Cookie session, String format, String query) throws Exception {
		return get(session, "/report?format=" + format + "&" + query).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsByteArray();
	}

	private static String pdfText(byte[] bytes) throws Exception {
		try (var reader = new PdfReader(bytes)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append(' ');
			}
			return all.toString().replaceAll("\\s+", " ");
		}
	}

	/** Filas de datos de una hoja, debajo del encabezado que empieza con {@code firstHeader}. */
	private static List<List<Object>> rows(XSSFWorkbook book, String sheet, String firstHeader) {
		List<List<Object>> rows = new ArrayList<>();
		boolean inTable = false;
		Sheet current = book.getSheet(sheet);
		for (Row row : current) {
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

	private static String hours(long minutes) {
		return "%d:%02d".formatted(minutes / 60, minutes % 60);
	}

	private void assertSameFigures(String query) throws Exception {
		String summary = get(analyst(), "/summary?" + query).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		int minutes = JsonPath.read(summary, "$.official.minutes");
		int windows = JsonPath.read(summary, "$.official.windows");
		int points = JsonPath.read(summary, "$.official.affectedPoints");
		int pointDays = JsonPath.read(summary, "$.official.pointDays");
		int recurrent = JsonPath.read(summary, "$.official.recurrentPoints");
		List<String> topNames = JsonPath.read(summary, "$.top[*].name");
		List<Integer> topMinutes = JsonPath.read(summary, "$.top[*].minutes");
		assertThat(minutes).isPositive();

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(download(analyst(), "XLSX", query)))) {
			var resumen = rows(book, "Resumen", "Sección");
			var porCargador = rows(book, "Por cargador", "Código");
			var ventanas = rows(book, "Ventanas", "Código");
			assertThat(value(resumen, "Tiempo confirmado fuera de línea (minutos)")).isEqualTo(minutes);
			assertThat(value(resumen, "Ventanas completas")).isEqualTo(windows);
			assertThat(value(resumen, "Cargadores afectados")).isEqualTo(points);
			assertThat(value(resumen, "Días-equipo")).isEqualTo(pointDays);
			assertThat(value(resumen, "Equipos recurrentes (3 o más días)")).isEqualTo(recurrent);
			// El detalle suma lo mismo que el resumen: por ventana y por cargador.
			assertThat(ventanas).hasSize(windows);
			assertThat(ventanas.stream().mapToDouble(row -> (Double) row.get(6)).sum()).isEqualTo(minutes);
			assertThat(porCargador).hasSize(points);
			assertThat(porCargador.stream().mapToDouble(row -> (Double) row.get(5)).sum()).isEqualTo(minutes);
			assertThat(porCargador.stream().mapToDouble(row -> (Double) row.get(2)).sum()).isEqualTo(pointDays);
			var top = resumen.stream().filter(row -> "Top 10".equals(row.get(0))).toList();
			assertThat(top).hasSize(topNames.size());
			for (int index = 0; index < top.size(); index++) {
				assertThat(String.valueOf(top.get(index).get(1))).contains(topNames.get(index));
				assertThat(((Double) top.get(index).get(2)).intValue()).isEqualTo(topMinutes.get(index));
			}
		}

		String pdf = pdfText(download(analyst(), "PDF", query));
		assertThat(pdf).contains("Durante el período se registraron")
				.contains(points + (points == 1 ? " cargador afectado" : " cargadores afectados"))
				.contains(pointDays + (pointDays == 1 ? " día-equipo" : " días-equipo"))
				.contains(windows + (windows == 1 ? " ventana completa" : " ventanas completas"))
				.contains(hours(minutes) + " de tiempo fuera de línea confirmado");
		for (int index = 0; index < topNames.size(); index++) {
			assertThat(pdf).contains(topNames.get(index));
			assertThat(pdf).contains(hours(topMinutes.get(index)));
		}
	}

	private static int value(List<List<Object>> summary, String concept) {
		return ((Double) summary.stream().filter(row -> concept.equals(row.get(1))).findFirst().orElseThrow().get(2))
				.intValue();
	}

	@Test
	void theDashboardTheExcelAndThePdfSayTheSameFiguresForTheSameFilters() throws Exception {
		seed();

		assertSameFigures(OCTOBER);
		assertSameFigures("preset=FIRST_HALF&month=2026-10");
		assertSameFigures(OCTOBER + "&equipmentIds=" + POINT);
		assertSameFigures(OCTOBER + "&equipmentIds=" + OTHER);
		assertSameFigures(OCTOBER + "&equipmentIds=" + POINT + "&equipmentIds=" + OTHER);
	}

	@Test
	void aWindowAcrossMidnightIsSplitBetweenDaysAndPeriodsAndAddsUpToTheSameTotal() throws Exception {
		insertWindow(POINT, at("2026-10-15", "23:50"), at("2026-10-16", "00:20"), "CONFIRMED");

		for (String query : new String[] { "preset=FIRST_HALF&month=2026-10", "preset=SECOND_HALF&month=2026-10" }) {
			String summary = get(analyst(), "/summary?" + query).andReturn().getResponse().getContentAsString();
			try (var book = new XSSFWorkbook(new ByteArrayInputStream(download(analyst(), "XLSX", query)))) {
				var ventanas = rows(book, "Ventanas", "Código");
				var resumen = rows(book, "Resumen", "Sección");

				assertThat(ventanas).hasSize(1);
				// La duracion total es 30 en ambos periodos; la porcion, 10 o 20.
				assertThat((Double) ventanas.getFirst().get(5)).isEqualTo(30.0);
				assertThat((Double) ventanas.getFirst().get(6)).isEqualTo(((Integer) JsonPath.read(summary, "$.official.minutes")).doubleValue());
				assertThat(value(resumen, "Tiempo confirmado fuera de línea (minutos)"))
						.isEqualTo((int) JsonPath.read(summary, "$.official.minutes"));
			}
		}
	}

	@Test
	void thePointShareIsOverTheCalendarDaysUpToTheCutOffDayIncludingTodayAndOverEveryDayOfAClosedPeriod() throws Exception {
		ZoneId zone = ZoneId.of("America/Guayaquil");
		LocalDate today = LocalDate.now(zone);
		LocalDate fortnightStart = today.getDayOfMonth() <= 15 ? today.withDayOfMonth(1) : today.withDayOfMonth(16);
		String preset = today.getDayOfMonth() <= 15 ? "FIRST_HALF" : "SECOND_HALF";
		insertWindow(POINT, at(fortnightStart.toString(), "00:10"), at(fortnightStart.toString(), "00:40"), "CONFIRMED");

		// Quincena en curso: dias calendario desde su inicio hasta hoy, incluido hoy.
		long openDays = ChronoUnit.DAYS.between(fortnightStart, today) + 1;
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(download(analyst(), "XLSX",
				"preset=" + preset + "&month=" + today.toString().substring(0, 7))))) {
			var points = rows(book, "Por cargador", "Código");
			double expected = java.math.BigDecimal.valueOf(100.0 / openDays).setScale(1, java.math.RoundingMode.HALF_UP)
					.doubleValue();

			assertThat((Double) points.getFirst().get(2)).isEqualTo(1.0);
			assertThat((Double) points.getFirst().get(3)).isEqualTo(expected);
		}

		// Periodo cerrado: todos sus dias (el mes de octubre de 2026 tiene 31).
		jdbc.execute("TRUNCATE itm_window_revision, itm_window CASCADE");
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-06", "08:00"), at("2026-10-06", "08:30"), "CONFIRMED");
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(download(analyst(), "XLSX", OCTOBER)))) {
			var points = rows(book, "Por cargador", "Código");

			assertThat((Double) points.getFirst().get(3)).isEqualTo(6.5);
		}
	}

	@Test
	void everyAuthenticatedRoleCanDownloadAndAnEmptyPeriodSaysSo() throws Exception {
		for (Role role : Role.values()) {
			for (String format : new String[] { "PDF", "XLSX" }) {
				assertThat(download(as(role), format, OCTOBER)).isNotEmpty();
			}
		}
		assertThat(pdfText(download(analyst(), "PDF", OCTOBER))).contains("No hay intermitencias confirmadas en el período.");
		get(analyst(), "/report?format=DOCX&" + OCTOBER).andExpect(status().isBadRequest());
		assertProblem(get(analyst(), "/report?format=PDF&preset=CUSTOM&from=2025-01-01&to=2026-11-01"), 422,
				"PERIOD_TOO_LONG");
	}
}
