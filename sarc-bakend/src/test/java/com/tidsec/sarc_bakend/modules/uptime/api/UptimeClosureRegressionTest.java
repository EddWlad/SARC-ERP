package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class UptimeClosureRegressionTest extends UptimeReportSupport {

	private void pending(String end) {
		UUID batch = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO upt_import_batch (id, source, origin, checksum, original_ref, status, declared_period_type,
				created_at, updated_at, version) VALUES (?, 'ENERLINK', 'PASTE', 'qa', 'qa', 'COMPLETED',
				'ROLLING_24H', now(), now(), 0)""", batch);
		jdbc.update("""
				INSERT INTO upt_import_row (id, batch_id, row_number, classification, issues, observations, approved,
				outcome, version, period_end) VALUES (?, ?, 1, 'PENDING_LINK', '[]', '[]', false, 'PENDING_LINK', 0, ?)
				""", UUID.randomUUID(), batch, java.sql.Timestamp.from(Instant.parse(end)));
	}

	@Test
	void emptyChosenRangeDoesNotCountOtherPeriods() throws Exception {
		pending("2026-10-01T05:00:00Z");
		pending("2026-10-20T05:00:00Z");
		for (String network : new String[] { "JAC", "KIA" }) {
			mvc.perform(get("/api/v1/uptime/summary").cookie(manager).param("from", "2026-10-19")
					.param("to", "2026-10-26").param("network", network)).andExpect(status().isOk())
					.andExpect(jsonPath("$.coverage.pendingLink").value(1));
		}
	}

	@Test
	void officialOpeningKeepsUnattributedPeriodWhenNetworkHasNoData() throws Exception {
		officialWeek(JAC_1, "10080", "0");
		pending("2026-10-19T05:00:00Z");
		pending("2026-09-01T05:00:00Z");
		for (String network : new String[] { "JAC", "KIA" }) {
			mvc.perform(get("/api/v1/uptime/summary").cookie(manager).param("network", network))
					.andExpect(status().isOk()).andExpect(jsonPath("$.coverage.pendingLink").value(1));
		}
	}

	@Test
	void bothReportsIncludeUnattributedCount() throws Exception {
		pending("2026-10-20T05:00:00Z");
		StringBuilder text = new StringBuilder();
		try (PdfReader reader = new PdfReader(report(manager, "PDF"))) {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
		}
		assertThat(text.toString()).contains("Pendientes de vincular (sin atribuir)");
		boolean found = false;
		try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(report(manager, "XLSX")))) {
			for (Row row : book.getSheet("Cobertura y exclusiones")) {
				if (row.getCell(0) != null && row.getCell(0).toString().equals("Pendientes de vincular (sin atribuir)")) {
					assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(1);
					found = true;
				}
			}
		}
		assertThat(found).isTrue();
	}
}
