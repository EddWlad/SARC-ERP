package com.tidsec.sarc_bakend.shared.importing.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TabularReaderTest {

	private final TabularReader reader = new TabularReader();

	@Test
	void readsSemicolonCsvWithQuotesBomAndBlankLines() {
		String csv = "﻿Serial number;Charger;Energy (kWh)\r\n"
				+ "1172071;\"MC DONALDS; ORELLANA\";\"8,07\"\r\n"
				+ "\r\n"
				+ "1172622;\"Plaza \"\"Norte\"\"\";0,77\r\n";

		List<TabularRow> rows = reader.read(csv.getBytes(StandardCharsets.UTF_8));

		assertThat(rows).hasSize(3);
		assertThat(rows.get(0).cells()).containsExactly("Serial number", "Charger", "Energy (kWh)");
		assertThat(rows.get(1).cells()).containsExactly("1172071", "MC DONALDS; ORELLANA", "8,07");
		assertThat(rows.get(2).number()).isEqualTo(4);
		assertThat(rows.get(2).cell(1)).isEqualTo("Plaza \"Norte\"");
	}

	@Test
	void readsTabSeparatedTextPastedFromASpreadsheet() {
		List<TabularRow> rows = reader.readText("ID\tINICIO\tACTIVO\n27151\t22-10-2026 23:45:12\tConectz\n");

		assertThat(rows).hasSize(2);
		assertThat(rows.get(1).cells()).containsExactly("27151", "22-10-2026 23:45:12", "Conectz");
	}

	@Test
	void anUnclosedQuoteFailsWithItsLineInsteadOfSwallowingTheRest() {
		// QA-04: antes se descartaban en silencio la fila abierta y todas las siguientes.
		assertThatThrownBy(() -> reader.readText("id,name\n1,ok\n2,\"sin cerrar\n3,perdida\n"))
				.extracting("code", "message")
				.containsExactly("IMPORT_UNCLOSED_QUOTE",
						"El archivo tiene comillas sin cerrar desde la linea 3; corrija ese campo y vuelva a cargarlo");
	}

	@Test
	void fallsBackToWindows1252ForSpanishExcelExports() {
		byte[] content = "ACTIVO,CARGADOR\nEstación,Peñón\n".getBytes(Charset.forName("windows-1252"));

		assertThat(reader.read(content).get(1).cells()).containsExactly("Estación", "Peñón");
	}

	@Test
	void readsWorkbookCellsAsTextWithIsoDatesAndExactNumbers() throws Exception {
		byte[] xlsx;
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Transacciones");
			sheet.createRow(0).createCell(2).setCellValue("Transacciones");
			Row header = sheet.createRow(1);
			header.createCell(0).setCellValue("ID");
			header.createCell(1).setCellValue("INICIO");
			header.createCell(2).setCellValue("ENERGIA CARGADA (kWh)");
			Row data = sheet.createRow(2);
			data.createCell(0).setCellValue(27151);
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd-mm-yyyy hh:mm:ss"));
			data.createCell(1).setCellValue(LocalDateTime.of(2026, 10, 22, 23, 45, 12));
			data.getCell(1).setCellStyle(dateStyle);
			data.createCell(2).setCellValue(22.42);
			workbook.write(out);
			xlsx = out.toByteArray();
		}

		List<TabularRow> rows = reader.read(xlsx);

		assertThat(rows).hasSize(3);
		assertThat(rows.get(1).number()).isEqualTo(2);
		assertThat(rows.get(2).cells()).containsExactly("27151", "2026-10-22 23:45:12", "22.42");
	}

	/** D09: solo la primera hoja es entrada; las demas no se importan, sea cual sea su contenido. */
	@Test
	void readsOnlyTheFirstSheetOfTheWorkbook() throws Exception {
		byte[] xlsx;
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			workbook.createSheet("Datos").createRow(0).createCell(0).setCellValue("primera");
			workbook.createSheet("Listas").createRow(0).createCell(0).setCellValue("segunda");
			workbook.write(out);
			xlsx = out.toByteArray();
		}

		List<TabularRow> rows = reader.read(xlsx);

		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().cells()).containsExactly("primera");
	}

	@Test
	void ignoresColumnsBeyondTheLimitSoAStrayFarCellCannotExhaustMemory() throws Exception {
		byte[] xlsx;
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Row row = workbook.createSheet().createRow(0);
			row.createCell(0).setCellValue("ID");
			row.createCell(16_000).setCellValue("lejos");
			workbook.write(out);
			xlsx = out.toByteArray();
		}

		List<TabularRow> rows = reader.read(xlsx);

		assertThat(rows.getFirst().cells()).hasSize(TabularReader.MAX_COLUMNS).startsWith("ID");
	}

	@Test
	void rejectsEmptyAndUnreadableContent() {
		assertThatThrownBy(() -> reader.read(new byte[0])).extracting("code").isEqualTo("IMPORT_FILE_EMPTY");
		assertThatThrownBy(() -> reader.read(new byte[] { 'P', 'K', 3, 4, 0, 0 }))
				.extracting("code").isEqualTo("IMPORT_FILE_UNREADABLE");
	}
}
