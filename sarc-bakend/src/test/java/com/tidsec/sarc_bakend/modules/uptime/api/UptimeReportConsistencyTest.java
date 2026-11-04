package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.jayway.jsonpath.JsonPath;

/** Dashboard, PDF y Excel presentan las mismas cifras (FR-037, SC-006). */
class UptimeReportConsistencyTest extends UptimeReportSupport {

	private static String pdfText(byte[] pdf) throws IOException {
		try (PdfReader reader = new PdfReader(pdf)) {
			StringBuilder text = new StringBuilder();
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				text.append(extractor.getTextFromPage(page));
			}
			return text.toString();
		}
	}

	private static String indicator(byte[] excel, String name) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			Sheet sheet = workbook.getSheet("Resumen");
			DataFormatter formatter = new DataFormatter();
			for (Row row : sheet) {
				if (name.equals(formatter.formatCellValue(row.getCell(0)))) {
					return formatter.formatCellValue(row.getCell(1)).replace(',', '.');
				}
			}
			throw new AssertionError("Sin indicador " + name);
		}
	}

	@Test
	void officialAndProvisionalFiguresMatchTheDashboard() throws Exception {
		officialWeek(JAC_1, "10041", "39");
		validate(registered(JAC_2, "1401", "39").get("id").toString());

		String summary = summary(manager);
		String official = JsonPath.read(summary, "$.official.uptimePercent");
		String provisional = JsonPath.read(summary, "$.provisional.uptimePercent");
		String offline = JsonPath.read(summary, "$.official.offlineHours");

		assertThat(pdfText(report(manager, "PDF"))).contains(official.replace('.', ',') + " %",
				provisional.replace('.', ',') + " %", offline.replace('.', ','));
		byte[] excel = report(manager, "XLSX");
		assertThat(Double.valueOf(indicator(excel, "Uptime oficial (%)"))).isEqualTo(Double.valueOf(official));
		assertThat(Double.valueOf(indicator(excel, "Uptime provisional (%)"))).isEqualTo(Double.valueOf(provisional));
		assertThat(Double.valueOf(indicator(excel, "Horas fuera de línea oficiales")))
				.isEqualTo(Double.valueOf(offline));
	}

	@Test
	void pendingMeasurementNeverReachesAReport() throws Exception {
		registered(JAC_1, "1296", "144");

		assertThat(pdfText(report(manager, "PDF"))).doesNotContain("90,00 %");
	}
}
