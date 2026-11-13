package com.tidsec.sarc_bakend.modules.intermittency.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * Registro operativo sintetico: reproduce las horas y los totales de las filas de hallazgos de la plantilla
 * real (tabla de spec.md), pero con puntos y codigos ficticios {@code 9xxxxx}. Genera las mismas filas como
 * XLSX, CSV con {@code ,} o {@code ;} y texto pegado con tabuladores, con o sin encabezado.
 *
 * <p>Cada fila se coloca en su numero de fila original; los huecos quedan vacios, como en un archivo real
 * al que se le quitaron filas. No lee {@code documents/plantillas/}.
 */
public final class SyntheticOperationalLog {

	private static final DateTimeFormatter DAY_TEXT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private SyntheticOperationalLog() {
	}

	/** Un par Inicio/Fin con horas {@code H:mm} o {@code HH:mm:ss}; vacio si falta. */
	public record Pair(String start, String end) {
		public static Pair of(String start, String end) {
			return new Pair(start, end);
		}
	}

	/**
	 * Una fila del registro. {@code date} es el dia; si es nulo se usa {@code rawDate} como texto
	 * (por ejemplo, una fecha inexistente). {@code total} es {@code H:mm} o nulo.
	 */
	public record Entry(int number, LocalDate date, String rawDate, String code, String name, String comment,
			List<Pair> pairs, String total) {

		public Entry {
			pairs = List.copyOf(pairs);
		}

		public int pairCount() {
			return pairs.size();
		}

		public Entry withCode(String newCode) {
			return new Entry(number, date, rawDate, newCode, name, comment, pairs, total);
		}
	}

	/** Si hay fila de encabezados y si hay columna Total inactivo. */
	public record Layout(boolean header, boolean total) {
		public static final Layout WITH_HEADER = new Layout(true, true);
		public static final Layout HEADERLESS = new Layout(false, true);
		public static final Layout HEADERLESS_NO_TOTAL = new Layout(false, false);
	}

	/** Como se escriben las horas en el XLSX. */
	public enum TimeCells {
		/** Numero decimal sin formato: fraccion del dia. */
		FRACTION,
		/** Celda con formato de hora ({@code hh:mm}); el total, con {@code [h]:mm}. */
		TIME_FORMAT
	}

	// ----- Filas -----

	/** Las filas de hallazgos de la plantilla real, con codigos ficticios. */
	public static List<Entry> standardRows() {
		String a = Feature008Fixtures.BLOCKED_CODE;
		String b = Feature008Fixtures.FREE_CODE;
		String c = Feature008Fixtures.DISCONNECTED_CODE;
		String d = Feature008Fixtures.SECOND_BLOCKED_CODE;
		String e = Feature008Fixtures.TEST_CODE;
		return List.of(
				// Fila 3: las ventanas 3 y 4 son identicas; el total cuenta dos veces la ventana (109 min).
				entry(3, 10, 1, a, pairs("12:35", "13:02", "16:49", "17:19", "17:55", "18:21", "17:55", "18:21"), "1:49"),
				// Fila 57: el fin 1 es anterior al inicio 1; requiere confirmar que cruza la medianoche.
				entry(57, 10, 4, d, pairs("17:00", "07:05"), "14:06"),
				// Fila 61: el fin 2 es anterior al inicio 2, probable error de tipeo.
				entry(61, 10, 4, b, pairs("08:10", "08:40", "19:41", "19:26"), "23:53"),
				// Filas 94 y 240: el fin esta pocos minutos antes del inicio.
				entry(94, 10, 6, c, pairs("10:03", "09:58"), "24:01"),
				// Fila 99: el total no coincide con las ventanas (52 + 46 = 98 min frente a 1331).
				entry(99, 10, 7, e, pairs("17:07", "17:59", "18:04", "18:50"), "22:11"),
				// Fila 117: el inicio 3 no tiene fin; el total ignora esa ventana.
				entry(117, 10, 7, a, pairs("09:00", "09:10", "12:00", "12:15", "16:01", ""), "0:25"),
				// Fila 155: el fin 4 es anterior al inicio 4 y el inicio 5 no tiene fin.
				entry(155, 10, 12, b, pairs("06:00", "06:30", "08:00", "08:20", "10:00", "10:10", "15:00", "14:50",
						"18:00", ""), "25:05"),
				// Fila 163: 20:03 a 12:22 del dia siguiente (979 min: 237 el dia 1 y 742 el dia 2).
				entry(163, 10, 12, d, pairs("20:03", "12:22"), "16:19"),
				// Fila 228: solapamiento; 02:24-02:30 y 02:25-03:11 (el total suma ambas: 52 min).
				entry(228, 10, 17, c, pairs("02:24", "02:30", "02:25", "03:11"), "0:52"),
				entry(240, 10, 18, e, pairs("14:20", "14:15"), "24:01"));
	}

	/** Una fila con siete pares de Inicio y Fin: no hay limite de cinco (FR-010). */
	public static Entry sevenPairs() {
		return entry(260, 10, 2, Feature008Fixtures.FREE_CODE, pairs("06:00", "06:10", "07:00", "07:10", "08:00",
				"08:10", "09:00", "09:10", "10:00", "10:10", "11:00", "11:10", "12:00", "12:10"), "1:10");
	}

	/** Una fila con segundos que no se pueden redondear al minuto: queda {@code SECONDS_AMBIGUOUS}. */
	public static Entry ambiguousSeconds() {
		return entry(261, 10, 3, Feature008Fixtures.DISCONNECTED_CODE, pairs("08:00:30", "08:20:00"), "0:20");
	}

	/** Una fecha escrita como texto {@code dd/MM/yyyy} en lugar de una celda de fecha. */
	public static Entry textDate() {
		return new Entry(262, null, "05/10/2026", Feature008Fixtures.BLOCKED_CODE, "Punto A", "Intermitencia",
				pairs("09:00", "09:30"), "0:30");
	}

	/** Una fecha que no existe en el calendario: queda {@code DATE_FORMAT}. */
	public static Entry invalidDate() {
		return new Entry(263, null, "31/02/2026", Feature008Fixtures.BLOCKED_CODE, "Punto A", "Intermitencia",
				pairs("09:00", "09:30"), "0:30");
	}

	/** Las filas estandar mas las filas con formatos y limites especiales. */
	public static List<Entry> allRows() {
		List<Entry> rows = new ArrayList<>(standardRows());
		rows.add(sevenPairs());
		rows.add(ambiguousSeconds());
		rows.add(textDate());
		rows.add(invalidDate());
		return rows;
	}

	private static Entry entry(int number, int month, int day, String code, List<Pair> pairs, String total) {
		return new Entry(number, LocalDate.of(2026, month, day), null, code, nameOf(code), "Intermitencia", pairs,
				total);
	}

	private static String nameOf(String code) {
		return Feature008Fixtures.POINT_NAMES.getOrDefault(code, "Punto");
	}

	private static List<Pair> pairs(String... times) {
		List<Pair> pairs = new ArrayList<>();
		for (int i = 0; i + 1 < times.length; i += 2) {
			pairs.add(Pair.of(times[i], times[i + 1]));
		}
		return pairs;
	}

	// ----- Salidas -----

	public static byte[] xlsx(List<Entry> rows, Layout layout, TimeCells cells) {
		return workbook(rows, layout, cells, true);
	}

	/** El mismo archivo sin la columna Codigo cargador: se rechaza con {@code IMPORT_HEADER_MISSING}. */
	public static byte[] xlsxWithoutCodeColumn(List<Entry> rows) {
		return workbook(rows, Layout.WITH_HEADER, TimeCells.FRACTION, false);
	}

	public static byte[] csv(List<Entry> rows, char separator, Layout layout) {
		return text(rows, separator, layout).getBytes(StandardCharsets.UTF_8);
	}

	/** Bloque copiado de una hoja de calculo: columnas separadas por tabulador. */
	public static String pasted(List<Entry> rows, Layout layout) {
		return text(rows, '\t', layout);
	}

	// ----- Implementacion -----

	private static List<String> headers(int pairs, boolean total, boolean codeColumn) {
		List<String> headers = new ArrayList<>(List.of("Fecha"));
		if (codeColumn) {
			headers.add("Código cargador");
		}
		headers.addAll(List.of("Nombre cargador", "Comentario"));
		for (int pair = 1; pair <= pairs; pair++) {
			headers.add("Inicio " + pair);
			headers.add("Fin " + pair);
		}
		if (total) {
			headers.add("Total inactivo");
		}
		return headers;
	}

	private static int pairColumns(List<Entry> rows) {
		return rows.stream().mapToInt(Entry::pairCount).max().orElse(0);
	}

	private static String text(List<Entry> rows, char separator, Layout layout) {
		int pairs = pairColumns(rows);
		int last = rows.stream().mapToInt(Entry::number).max().orElse(0);
		List<String> lines = new ArrayList<>();
		for (int number = 1; number <= last; number++) {
			lines.add("");
		}
		if (layout.header()) {
			lines.set(0, join(headers(pairs, layout.total(), true), separator));
		}
		for (Entry row : rows) {
			if (layout.header() && row.number() < 2) {
				throw new IllegalArgumentException("Con encabezado, las filas empiezan en la 2");
			}
			List<String> cells = new ArrayList<>(List.of(row.date() == null ? row.rawDate() : DAY_TEXT.format(row.date()),
					row.code(), row.name(), row.comment()));
			for (int pair = 0; pair < pairs; pair++) {
				Pair value = pair < row.pairCount() ? row.pairs().get(pair) : Pair.of("", "");
				cells.add(value.start());
				cells.add(value.end());
			}
			if (layout.total()) {
				cells.add(row.total() == null ? "" : row.total());
			}
			lines.set(row.number() - 1, join(cells, separator));
		}
		return String.join("\n", lines) + "\n";
	}

	private static String join(List<String> cells, char separator) {
		return cells.stream().map(cell -> quote(cell, separator)).collect(Collectors.joining(String.valueOf(separator)));
	}

	private static String quote(String cell, char separator) {
		return cell.indexOf(separator) >= 0 || cell.contains("\"") || cell.contains("\n")
				? '"' + cell.replace("\"", "\"\"") + '"' : cell;
	}

	private static byte[] workbook(List<Entry> rows, Layout layout, TimeCells cells, boolean codeColumn) {
		try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = book.createSheet("Intermitencias");
			CreationHelper helper = book.getCreationHelper();
			CellStyle dateStyle = book.createCellStyle();
			dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy"));
			CellStyle timeStyle = book.createCellStyle();
			timeStyle.setDataFormat(helper.createDataFormat().getFormat("hh:mm"));
			CellStyle totalStyle = book.createCellStyle();
			totalStyle.setDataFormat(helper.createDataFormat().getFormat("[h]:mm"));
			int pairs = pairColumns(rows);
			if (layout.header()) {
				Row header = sheet.createRow(0);
				List<String> names = headers(pairs, layout.total(), codeColumn);
				for (int column = 0; column < names.size(); column++) {
					header.createCell(column).setCellValue(names.get(column));
				}
			}
			for (Entry entry : rows) {
				Row row = sheet.createRow(entry.number() - 1);
				int column = 0;
				if (entry.date() == null) {
					row.createCell(column++).setCellValue(entry.rawDate());
				}
				else {
					var cell = row.createCell(column++);
					cell.setCellValue(entry.date());
					cell.setCellStyle(dateStyle);
				}
				if (codeColumn) {
					row.createCell(column++).setCellValue(entry.code());
				}
				row.createCell(column++).setCellValue(entry.name());
				row.createCell(column++).setCellValue(entry.comment());
				for (int pair = 0; pair < pairs; pair++) {
					Pair value = pair < entry.pairCount() ? entry.pairs().get(pair) : Pair.of("", "");
					column = time(row, column, value.start(), cells, timeStyle);
					column = time(row, column, value.end(), cells, timeStyle);
				}
				if (layout.total() && entry.total() != null) {
					var cell = row.createCell(column);
					cell.setCellValue(fraction(entry.total()));
					if (cells == TimeCells.TIME_FORMAT) {
						cell.setCellStyle(totalStyle);
					}
				}
			}
			book.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static int time(Row row, int column, String value, TimeCells cells, CellStyle timeStyle) {
		if (!value.isBlank()) {
			var cell = row.createCell(column);
			cell.setCellValue(fraction(value));
			if (cells == TimeCells.TIME_FORMAT) {
				cell.setCellStyle(timeStyle);
			}
		}
		return column + 1;
	}

	/** {@code H:mm} o {@code HH:mm:ss} como fraccion del dia; con mas de 24 h, mayor que 1. */
	static double fraction(String time) {
		String[] parts = time.split(":");
		double seconds = Integer.parseInt(parts[0]) * 3600.0 + Integer.parseInt(parts[1]) * 60.0
				+ (parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
		return seconds / 86_400.0;
	}
}
