package com.tidsec.sarc_bakend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;

/**
 * QA opt-in: vuelca cifras de Consumo con datos y filtros fijos a {@code target/}, sobre base desechable.
 * T002 lo ejecuta antes de tocar Consumo y T119 despues; la diferencia entre ambos volcados es el cambio.
 */
@EnabledIfSystemProperty(named = "sarc.qa.baseline", matches = ".+")
@SpringBootTest(properties = { "sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class Feature005ConsumptionBaseline {

	private static final String RANGE = "from=2026-10-01&to=2026-11-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	FileStorage storage;

	@Test
	void dump() throws Exception {
		jdbc.execute("truncate energy_import_row,energy_session_revision,energy_session_review,energy_session,"
				+ "energy_import_batch,asset_equipment,report_issued cascade");
		Feature004Fixtures.seed(jdbc, false);
		when(storage.storeDocument(anyString(), anyString(), any())).thenReturn("qa005/private");
		List<String> lines = new ArrayList<>();
		for (String role : List.of("GERENTE", "ANALISTA")) {
			lines.add("## " + role + " summary");
			lines.add(new String(fetch(role, "/api/v1/energy/summary?" + RANGE), java.nio.charset.StandardCharsets.UTF_8)
					.replaceAll("\"(cutOff|generatedAt)\":\"[^\"]*\"", "\"$1\":\"-\""));
			lines.add("## " + role + " PDF");
			lines.addAll(pdf(fetch(role, "/api/v1/energy/report?format=PDF&" + RANGE)));
			lines.add("## " + role + " XLSX");
			lines.addAll(excel(fetch(role, "/api/v1/energy/report?format=XLSX&" + RANGE)));
		}
		Files.write(Path.of("target/consumption-" + System.getProperty("sarc.qa.baseline") + ".txt"), lines);
	}

	private byte[] fetch(String role, String url) throws Exception {
		return mvc.perform(get(url).with(user("qa").roles(role))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsByteArray();
	}

	private static List<String> pdf(byte[] bytes) throws Exception {
		List<String> lines = new ArrayList<>();
		try (PdfReader reader = new PdfReader(bytes)) {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				extractor.getTextFromPage(page).lines().filter(line -> !line.contains("Corte")
						&& !line.contains("Generado") && !line.contains("Página")).forEach(lines::add);
			}
		}
		return lines;
	}

	private static List<String> excel(byte[] bytes) throws Exception {
		List<String> lines = new ArrayList<>();
		try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			DataFormatter formatter = new DataFormatter();
			for (Sheet sheet : book) {
				for (Row row : sheet) {
					if (row.getRowNum() >= 4) {
						StringBuilder line = new StringBuilder(sheet.getSheetName());
						row.forEach(cell -> line.append('|').append(formatter.formatCellValue(cell)));
						lines.add(line.toString());
					}
				}
			}
		}
		return lines;
	}
}
