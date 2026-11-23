package com.tidsec.sarc_bakend.modules.incident.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.BeanUtils;

import com.tidsec.sarc_bakend.modules.incident.application.result.ControlReportView;
import com.tidsec.sarc_bakend.shared.report.CorporateBranding;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** FR-070 a FR-077: el PDF y el Excel de «Control de tickets» muestran lo mismo, con la presentacion corporativa. */
class ControlReportTest {

	private static final Instant CUT_OFF = Instant.parse("2026-11-22T20:30:00Z");
	private static final String SHA = "a".repeat(64);

	private static CorporateBranding branding() {
		try {
			return BeanUtils.instantiateClass(CorporateBranding.class.getDeclaredConstructor(OperatingZone.class),
					new OperatingZone(ZoneId.of("America/Guayaquil")));
		}
		catch (NoSuchMethodException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static ControlReportView view(String period, String filters, List<ControlReportView.Line> lines) {
		long open = lines.stream().filter(line -> "Abierto".equals(line.state())).count();
		return new ControlReportView(period, filters, CUT_OFF, lines, open, lines.size() - open, SHA);
	}

	private static List<ControlReportView.Line> sample() {
		return List.of(new ControlReportView.Line("TICKET-086", "Electrolinera X / Conectores 1 y 2", "Abierto"),
				new ControlReportView.Line("TICKET-087", "Servicio de chatbot", "Cerrado"),
				new ControlReportView.Line("TICKET-088", "CC El Bosque / Barrera vehicular", "Abierto"));
	}

	private static String pdfText(byte[] bytes) throws Exception {
		try (var reader = new PdfReader(bytes)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
			}
			return all.toString();
		}
	}

	private static List<String> column(Sheet sheet, int first, int column) {
		DataFormatter formatter = new DataFormatter();
		List<String> values = new ArrayList<>();
		for (int row = first; row <= sheet.getLastRowNum(); row++) {
			values.add(sheet.getRow(row) == null || sheet.getRow(row).getCell(column) == null ? ""
					: formatter.formatCellValue(sheet.getRow(row).getCell(column)));
		}
		return values;
	}

	@Test
	void thePdfHasTheCorporateLayoutOfFr070() throws Exception {
		byte[] pdf = new ControlReportPdfRenderer(branding()).render(view("Todo el historial", "Sin filtros", sample()));

		String text = pdfText(pdf);
		assertThat(text).contains("CONTROL DE TICKETS", "Electromovilidad | Puntos de carga y electrolineras",
				"REGISTRO OPERATIVO", "NÚMERO DE TICKET", "CARGADOR O ELECTROLINERA", "ESTADO DEL TICKET");
		assertThat(text).contains("Todo el historial", "Sin filtros", "Zona horaria: America/Guayaquil", "Fecha de corte: 22/11/2026 15:30");
		try (var reader = new PdfReader(pdf)) {
			assertThat(reader.getPageSize(1).getWidth()).as("A4 vertical").isLessThan(reader.getPageSize(1).getHeight());
			var xobjects = reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT);
			assertThat(xobjects.getKeys()).as("los dos logos").hasSizeGreaterThanOrEqualTo(2);
		}
	}

	@Test
	void thePdfKeepsTheTicketOrderWithTheThreeColumnsAndTheTotalsFooter() throws Exception {
		String text = pdfText(new ControlReportPdfRenderer(branding()).render(view("Período: 01/10/2026 al 31/10/2026",
				"Filtros: Estado: Abierto", sample())));

		assertThat(text.indexOf("TICKET-086")).isPositive().isLessThan(text.indexOf("TICKET-087"));
		assertThat(text.indexOf("TICKET-087")).isLessThan(text.indexOf("TICKET-088"));
		assertThat(text).contains("Electrolinera X / Conectores 1 y 2", "Servicio de chatbot",
				"CC El Bosque / Barrera vehicular");
		assertThat(text).contains("Total: 3 · Abiertos: 2 · Cerrados: 1").containsPattern("Página 1 de ?1");
		assertThat(text).contains("Período: 01/10/2026 al 31/10/2026", "Filtros: Estado: Abierto");
	}

	@Test
	void aLongListGetsPageXOfYOnEveryPage() throws Exception {
		List<ControlReportView.Line> many = new ArrayList<>();
		for (int index = 1; index <= 120; index++) {
			many.add(new ControlReportView.Line("TICKET-" + (1000 + index), "Equipo de prueba " + index,
					index % 3 == 0 ? "Cerrado" : "Abierto"));
		}

		String text = pdfText(new ControlReportPdfRenderer(branding()).render(view("Todo el historial", "Sin filtros", many)));

		assertThat(text).containsPattern("Página 1 de ?3").containsPattern("Página 2 de ?3").containsPattern("Página 3 de ?3")
				.contains("Total: 120 · Abiertos: 80 · Cerrados: 40");
	}

	@Test
	void anEmptyReportKeepsTheHeaderAndSaysThereAreNoTickets() throws Exception {
		byte[] pdf = new ControlReportPdfRenderer(branding()).render(view("Período: 01/10/2026 al 31/10/2026", "Sin filtros", List.of()));

		String text = pdfText(pdf);
		assertThat(text).contains("CONTROL DE TICKETS", "REGISTRO OPERATIVO", "Sin tickets en el período");
		assertThat(text).doesNotContain("NÚMERO DE TICKET");

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(new ControlReportExcelRenderer(branding())
				.render(view("Período: 01/10/2026 al 31/10/2026", "Sin filtros", List.of()))))) {
			assertThat(column(book.getSheet("Control"), 5, 0)).contains("Sin tickets en el período");
		}
	}

	@Test
	void theExcelHasTheSameRowsInTheSameOrderAsThePdfAndACriteriaSheet() throws Exception {
		ControlReportView view = view("Todo el historial", "Sin filtros", sample());

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(new ControlReportExcelRenderer(branding()).render(view)))) {
			assertThat(book.getSheetName(0)).isEqualTo("Control");
			assertThat(book.getSheetName(1)).isEqualTo("Criterios");
			Sheet control = book.getSheet("Control");
			assertThat(control.getRow(0).getCell(0).getStringCellValue()).isEqualTo("CONTROL DE TICKETS");
			assertThat(column(control, 1, 0).subList(0, 2)).anyMatch(value -> value.contains("Todo el historial"));
			int header = 4;
			assertThat(column(control, header, 0)).startsWith("NÚMERO DE TICKET");
			assertThat(column(control, header, 1)).startsWith("CARGADOR O ELECTROLINERA");
			assertThat(column(control, header, 2)).startsWith("ESTADO DEL TICKET");
			assertThat(column(control, header + 1, 0).subList(0, 3)).containsExactly("TICKET-086", "TICKET-087", "TICKET-088");
			assertThat(column(control, header + 1, 1).subList(0, 3)).containsExactly("Electrolinera X / Conectores 1 y 2",
					"Servicio de chatbot", "CC El Bosque / Barrera vehicular");
			assertThat(column(control, header + 1, 2).subList(0, 3)).containsExactly("Abierto", "Cerrado", "Abierto");
			assertThat(column(control, header + 1, 0)).contains("Total: 3 · Abiertos: 2 · Cerrados: 1");

			Sheet criteria = book.getSheet("Criterios");
			assertThat(column(criteria, 5, 0)).contains("Período", "Filtros", "Zona horaria", "Corte",
					"Huella de las filas (SHA-256)");
			assertThat(column(criteria, 5, 1)).contains("Todo el historial", "America/Guayaquil", SHA);
		}
	}
}
