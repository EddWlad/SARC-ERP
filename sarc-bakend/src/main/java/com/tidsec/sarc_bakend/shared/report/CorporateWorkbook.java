package com.tidsec.sarc_bakend.shared.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Libro Excel con la estructura comun: cada hoja empieza con titulo, alcance y fecha de corte, y
 * luego una tabla con encabezado fijo y filtro. Pensado para verificar las cifras del PDF
 * (PRD RF-RPT-04), por eso guarda los valores como datos tabulares, no como texto decorado.
 */
public final class CorporateWorkbook {

	private final XSSFWorkbook workbook = new XSSFWorkbook();
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
		headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);
		headerStyle.setFillForegroundColor(new XSSFColor(new byte[] { 0x51, 0x43, 0x6E }));
		headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
	}

	public static CorporateWorkbook create(CorporateBranding branding, String scope, Instant cutOff) {
		return new CorporateWorkbook(branding, scope, cutOff);
	}

	/** Agrega una hoja. Los valores numericos se escriben como numeros para poder sumarlos. */
	public CorporateWorkbook sheet(String name, String title, List<String> headers, List<List<Object>> rows) {
		Sheet sheet = workbook.createSheet(name);
		Row titleRow = sheet.createRow(0);
		titleRow.createCell(0).setCellValue(title);
		titleRow.getCell(0).setCellStyle(titleStyle);
		sheet.createRow(1).createCell(0).setCellValue(scope);
		sheet.createRow(2).createCell(0).setCellValue(generated);
		int headerIndex = 4;
		Row headerRow = sheet.createRow(headerIndex);
		for (int column = 0; column < headers.size(); column++) {
			headerRow.createCell(column).setCellValue(headers.get(column));
			headerRow.getCell(column).setCellStyle(headerStyle);
		}
		for (int index = 0; index < rows.size(); index++) {
			Row row = sheet.createRow(headerIndex + 1 + index);
			List<Object> values = rows.get(index);
			for (int column = 0; column < values.size(); column++) {
				Object value = values.get(column);
				if (value instanceof Number number) {
					row.createCell(column).setCellValue(number.doubleValue());
				}
				else {
					row.createCell(column).setCellValue(value == null ? "" : value.toString());
				}
			}
		}
		sheet.createFreezePane(0, headerIndex + 1);
		if (!rows.isEmpty()) {
			sheet.setAutoFilter(new CellRangeAddress(headerIndex, headerIndex + rows.size(), 0,
					headers.size() - 1));
		}
		for (int column = 0; column < headers.size(); column++) {
			sheet.autoSizeColumn(column);
		}
		return this;
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
