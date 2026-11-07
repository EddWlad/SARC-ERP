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
        java.nio.file.Files.write(java.nio.file.Path.of("target/feature006-report-full.xlsx"), excel);
        java.nio.file.Files.write(java.nio.file.Path.of("target/feature006-report-full.pdf"), report(manager,"PDF"));
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
    @Test void weeklyReportHasReferenceMeanAndNoInventedEvaluatedMinutes() throws Exception {
        java.util.UUID id=java.util.UUID.randomUUID();
        jdbc.update("""
            INSERT INTO upt_measurement(id,connector_id,equipment_id,source,period_zone,latest_revision,effective_revision,
                period_precision,reference_week,reference_period_type,created_at,updated_at,version)
            VALUES(?,?,?,'ENERLINK','America/Guayaquil',1,1,'REFERENCE_WEEK','2026-10-26','ROLLING_7D',now(),now(),0)
            """,id,JAC_1,com.tidsec.sarc_bakend.support.Feature005Fixtures.JAC_STATION);
        jdbc.update("""
            INSERT INTO upt_measurement_revision(id,measurement_id,revision,reading_class,period_type,available_minutes,
                fault_minutes,offline_minutes,reported_percent,review_state,observations,origin,equipment_label,
                connector_type_label,entered_at,validated_at,uptime_basis)
            VALUES(?,?,1,'MEASURED','ROLLING_7D',9815,0,234,97.6800,'VALIDATED','[]','FILE','Anterior','Tipo2',now(),now(),'REPORTED_PERCENT')
            """,java.util.UUID.randomUUID(),id);
        for(String format:java.util.List.of("PDF","XLSX")) {
            var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/uptime/report")
                .cookie(manager).param("referenceWeek","2026-10-26").param("format",format))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse();
            java.nio.file.Files.write(java.nio.file.Path.of("target/feature006-report-reference."+format.toLowerCase()),response.getContentAsByteArray());
            if(format.equals("PDF"))assertThat(pdfText(response.getContentAsByteArray())).contains("Informe sin horario real","97,68 %","REPORT_MEAN");
            else {
                assertThat(Double.valueOf(indicator(response.getContentAsByteArray(),"Promedio del informe (%)"))).isEqualTo(97.68);
                try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                    assertThat(book.getNumberOfSheets()).isEqualTo(7);
                    String cells="";var formatter=new DataFormatter();for(var row:book.getSheet("Por conector"))for(var cell:row)cells+=formatter.formatCellValue(cell)+" ";
                    assertThat(cells).contains("REPORT_MEAN","No informado");
                }
            }
        }
    }

}
