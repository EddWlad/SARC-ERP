package com.tidsec.sarc_bakend.shared.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisOrientation;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.DisplayBlanks;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Libro Excel con la estructura comun: cada hoja empieza con titulo, alcance y fecha de corte, y
 * luego una tabla con encabezado fijo y filtro. Pensado para verificar las cifras del PDF
 * (PRD RF-RPT-04), por eso guarda los valores como datos tabulares, no como texto decorado. Se
 * escribe en streaming (pocas filas en memoria) porque el detalle de transacciones puede ser grande.
 */
public final class CorporateWorkbook {

	private static final int ROWS_IN_MEMORY = 200;
	private static final int MAX_COLUMN_CHARS = 60;
	private static final int HEADER_ROW = 4;
	private static final int COLUMNS_PER_CHART = 3;
	private static final int CHART_COLUMNS = 12;
	private static final int CHART_ROWS = 18;

	/** Una grafica: {@code line} para tendencia, barras en otro caso; un valor nulo es desconocido. */
	public record Chart(String title, String unit, boolean line, List<String> labels, List<BigDecimal> values) {
	}

	private final SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY);
	private final CellStyle titleStyle;
	private final XSSFCellStyle headerStyle;
	private final String scope;
	private final String generated;

	private CorporateWorkbook(CorporateBranding branding, String scope, Instant cutOff) {
		this.scope = scope;
		this.generated = "Fecha de corte: " + branding.dateTimeFormatter().format(cutOff) + " ("
				+ branding.zone().getId() + ")";
		Font titleFont = workbook.createFont();
		titleFont.setBold(true);
		titleFont.setFontHeightInPoints((short) 14);
		titleStyle = workbook.createCellStyle();
		titleStyle.setFont(titleFont);
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerFont.setColor(IndexedColors.WHITE.getIndex());
		headerStyle = (XSSFCellStyle) workbook.createCellStyle();
		headerStyle.setFont(headerFont);
		headerStyle.setFillForegroundColor(new XSSFColor(new byte[] { 0x51, 0x43, 0x6E }));
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
	}

	public static CorporateWorkbook create(CorporateBranding branding, String scope, Instant cutOff) {
		return new CorporateWorkbook(branding, scope, cutOff);
	}

	/** Agrega una hoja. Los valores numericos se escriben como numeros para poder sumarlos. */
	public CorporateWorkbook sheet(String name, String title, List<String> headers, List<List<Object>> rows) {
		write(name, title, headers, rows, true);
		return this;
	}

	/**
	 * Agrega una hoja con los datos de cada grafica y su grafica nativa. Los rangos apuntan a esas
	 * celdas y la cache se llena desde los mismos valores: nunca se releen filas ya expulsadas del
	 * streaming. Un valor nulo queda como celda vacia y corta la linea.
	 */
	public CorporateWorkbook charts(String name, String title, List<Chart> charts) {
		List<String> headers = new ArrayList<>();
		int rowCount = 0;
		for (Chart chart : charts) {
			headers.addAll(List.of(chart.title(), chart.unit(), ""));
			rowCount = Math.max(rowCount, chart.labels().size());
		}
		List<List<Object>> rows = new ArrayList<>();
		for (int row = 0; row < rowCount; row++) {
			List<Object> values = new ArrayList<>();
			for (Chart chart : charts) {
				boolean present = row < chart.labels().size();
				values.add(present ? chart.labels().get(row) : null);
				values.add(present ? chart.values().get(row) : null);
				values.add(null);
			}
			rows.add(values);
		}
		write(name, title, headers, rows, false);
		XSSFDrawing drawing = workbook.getXSSFWorkbook().getSheet(name).createDrawingPatriarch();
		for (int index = 0; index < charts.size(); index++) {
			draw(drawing, name, charts.get(index), index, charts.size() * COLUMNS_PER_CHART);
		}
		return this;
	}

	private static void draw(XSSFDrawing drawing, String sheet, Chart data, int index, int firstColumn) {
		int firstRow = HEADER_ROW + index * CHART_ROWS;
		XSSFChart chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, firstColumn, firstRow,
				firstColumn + CHART_COLUMNS, firstRow + CHART_ROWS - 1));
		chart.setTitleText(data.title() + " (" + data.unit() + ")");
		chart.setTitleOverlay(false);
		chart.displayBlanksAs(DisplayBlanks.GAP);
		XDDFCategoryAxis categories = chart.createCategoryAxis(AxisPosition.BOTTOM);
		XDDFValueAxis values = chart.createValueAxis(AxisPosition.LEFT);
		categories.setVisible(true);
		values.setVisible(true);
		values.setCrosses(data.line() ? AxisCrosses.AUTO_ZERO : AxisCrosses.MAX);
		values.setMinimum(0);
		int column = index * COLUMNS_PER_CHART;
		int size = data.labels().size();
		XDDFChartData plot = chart.createData(data.line() ? ChartTypes.LINE : ChartTypes.BAR, categories, values);
		XDDFChartData.Series series = plot.addSeries(
				XDDFDataSourcesFactory.fromArray(data.labels().toArray(String[]::new), range(sheet, column, size)),
				XDDFDataSourcesFactory.fromArray(numbers(data.values()), range(sheet, column + 1, size)));
		series.setTitle(data.unit(), null);
		plot.setVaryColors(false);
		if (plot instanceof XDDFBarChartData bars) {
			bars.setBarDirection(BarDirection.BAR);
			categories.setOrientation(AxisOrientation.MAX_MIN);
		}
		if (series instanceof XDDFLineChartData.Series line) {
			line.setSmooth(false);
			line.setMarkerStyle(MarkerStyle.CIRCLE);
		}
		chart.plot(plot);
	}

	private static Double[] numbers(List<BigDecimal> values) {
		return values.stream().map(value -> value == null ? null : value.doubleValue()).toArray(Double[]::new);
	}

	private static String range(String sheet, int column, int size) {
		return new AreaReference(new CellReference(sheet, HEADER_ROW + 1, column, true, true),
				new CellReference(sheet, HEADER_ROW + size, column, true, true), SpreadsheetVersion.EXCEL2007)
				.formatAsString();
	}

	private void write(String name, String title, List<String> headers, List<List<Object>> rows, boolean filter) {
		SXSSFSheet sheet = workbook.createSheet(name);
		Row titleRow = sheet.createRow(0);
		titleRow.createCell(0).setCellValue(title);
		titleRow.getCell(0).setCellStyle(titleStyle);
		sheet.createRow(1).createCell(0).setCellValue(scope);
		sheet.createRow(2).createCell(0).setCellValue(generated);
		int headerIndex = HEADER_ROW;
		Row headerRow = sheet.createRow(headerIndex);
		int[] widths = new int[headers.size()];
		for (int column = 0; column < headers.size(); column++) {
			widths[column] = headers.get(column).length();
			headerRow.createCell(column).setCellValue(headers.get(column));
			headerRow.getCell(column).setCellStyle(headerStyle);
		}
		for (int index = 0; index < rows.size(); index++) {
			Row row = sheet.createRow(headerIndex + 1 + index);
			List<Object> values = rows.get(index);
			for (int column = 0; column < values.size(); column++) {
				Object value = values.get(column);
				if (value == null) {
					continue;
				}
				String text = value.toString();
				if (value instanceof Number number) {
					row.createCell(column).setCellValue(number.doubleValue());
				}
				else {
					row.createCell(column).setCellValue(text);
				}
				if (column < widths.length) {
					widths[column] = Math.max(widths[column], text.length());
				}
			}
		}
		sheet.createFreezePane(0, headerIndex + 1);
		if (filter && !rows.isEmpty()) {
			sheet.setAutoFilter(new CellRangeAddress(headerIndex, headerIndex + rows.size(), 0,
					headers.size() - 1));
		}
		// Ancho por longitud de texto: el autoajuste de POI mide cada celda con fuentes y es lento en hojas grandes.
		for (int column = 0; column < widths.length; column++) {
			sheet.setColumnWidth(column, Math.min(widths[column] + 3, MAX_COLUMN_CHARS) * 256);
		}
	}

	public byte[] finish() {
		try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			workbook.write(output);
			return output.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}
