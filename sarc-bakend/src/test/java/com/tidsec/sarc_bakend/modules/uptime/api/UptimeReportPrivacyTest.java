package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.support.WebSessions;

/** El reporte no expone identificadores tecnicos ni motivos, y no cambia por rol (FR-039, FR-042, R-14). */
class UptimeReportPrivacyTest extends UptimeReportSupport {

	private static final String JUSTIFICATION = "Motivo reservado QA005";

	private byte[] asRole(Role role, String format) throws Exception {
		return mvc.perform(get(REPORT).param("format", format).with(user("qa").roles(role.name())))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private static String pdf(byte[] bytes) throws Exception {
		try (PdfReader reader = new PdfReader(bytes)) {
			StringBuilder text = new StringBuilder();
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				text.append(extractor.getTextFromPage(page));
			}
			return text.toString();
		}
	}

	/** Datos de cada hoja; el corte de cabecera cambia en cada emision. */
	private static List<String> rows(byte[] bytes) throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			DataFormatter formatter = new DataFormatter();
			List<String> rows = new ArrayList<>();
			for (Sheet sheet : workbook) {
				for (Row row : sheet) {
					if (row.getRowNum() >= 4) {
						StringBuilder line = new StringBuilder(sheet.getSheetName());
						row.forEach(cell -> line.append('|').append(formatter.formatCellValue(cell)));
						rows.add(line.toString());
					}
				}
			}
			return rows;
		}
	}

	private void seedWithReservedReason() throws Exception {
		String id = officialWeek(JAC_1, "10041", "39");
		String body = correction(version(id), "10000", "80").replace("ROLLING_24H", "EXACT_WEEK")
				.replace("\"exactConfirmed\":false", "\"exactConfirmed\":true").replace("Dato corregido", JUSTIFICATION);
		mvc.perform(post("/api/v1/uptime/measurements/" + id + "/corrections").cookie(analyst).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
		validate(id);
		String excluded = registered(JAC_2, "1401", "39").get("id").toString();
		validate(excluded);
		mvc.perform(post("/api/v1/uptime/measurements/" + excluded + "/exclude").cookie(analyst).with(WebSessions.xsrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":%d,\"justification\":\"%s\"}"
						.formatted(version(excluded), JUSTIFICATION)))
				.andExpect(status().is2xxSuccessful());
	}

	@Test
	void noTechnicalIdentifiersNorReservedReasons() throws Exception {
		seedWithReservedReason();

		for (byte[] content : List.of(report(manager, "PDF"), report(analyst, "PDF"))) {
			assertThat(pdf(content)).doesNotContain(JUSTIFICATION).doesNotContainIgnoringCase("ocpp")
					.doesNotContain("UID", "PUK");
		}
		String cells = String.join("\n", rows(report(analyst, "XLSX")));
		assertThat(cells).doesNotContain(JUSTIFICATION).doesNotContainIgnoringCase("ocpp").doesNotContain("UID", "PUK");
	}

	@Test
	void contentIsTheSameForTheFiveRoles() throws Exception {
		seedWithReservedReason();

		Set<String> distinct = new TreeSet<>();
		for (Role role : Role.values()) {
			distinct.add(String.join("\n", rows(asRole(role, "XLSX"))));
		}
		assertThat(distinct).hasSize(1);
	}
}
