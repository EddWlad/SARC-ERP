package com.tidsec.sarc_bakend.modules.consumption.infrastructure.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;

@SpringBootTest(
		properties = {"sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsumptionReportPrivacyTest {
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@MockitoBean FileStorage storage;
	@MockitoSpyBean ConsumptionPdfRenderer pdf;

	@BeforeEach
	void seed() {
		jdbc.execute(
				"truncate"
					+ " energy_import_row,energy_session_revision,energy_session_review,energy_session,energy_import_batch,asset_equipment,report_issued"
					+ " cascade");
		Feature004Fixtures.seed(jdbc, false);
		when(storage.storeDocument(anyString(), anyString(), any())).thenReturn("qa004/private");
	}

	private byte[] generate(String role, String format) throws Exception {
		return mvc.perform(
						get("/api/v1/energy/report?from=2026-10-01&to=2026-11-01&format=" + format)
								.with(user("qa").roles(role)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsByteArray();
	}

	@Test
	void managerialRendererNeverReceivesOperationalPayloadAndArchiveIsImmutable() throws Exception {
		byte[] bytes = generate("GERENTE", "PDF");
		verify(pdf)
				.render(
						argThat(
								r ->
										r.excluded().isEmpty()
												&& r.summary().pendingLink() == null
												&& r.summary().energyRejected() == null));
		try (PdfReader reader = new PdfReader(bytes)) {
			StringBuilder text = new StringBuilder();
			var extractor = new PdfTextExtractor(reader);
			for (int i = 1; i <= reader.getNumberOfPages(); i++)
				text.append(extractor.getTextFromPage(i));
			assertThat(text.toString())
					.doesNotContain(
							"Fuera de los indicadores oficiales",
							"Energía rechazada",
							"Energía por revisar",
							"QA004 HISTORIA OPERATIVA RESTRINGIDA")
					.contains("Aprobadas sobre umbral");
		}
		var original =
				jdbc.queryForMap(
						"select id,checksum,file_ref,created_at,cut_off from report_issued order by"
							+ " created_at desc limit 1");
		generate("GERENTE", "PDF");
		assertThat(
						jdbc.queryForMap(
								"select id,checksum,file_ref,created_at,cut_off from report_issued"
									+ " where id=?",
								original.get("id")))
				.isEqualTo(original);
		assertThat(jdbc.queryForObject("select count(*) from report_issued", Integer.class))
				.isEqualTo(2);
	}

	@Test
	void workbookAllSheetsContainOnlyAuthorizedData() throws Exception {
		assertThat(ConsumptionExcelRendererTest.packageText(generate("GERENTE", "XLSX")))
				.doesNotContain(
						"Excluidas",
						"Pendientes de vincular",
						"Energía rechazada",
						"QA004 HISTORIA OPERATIVA RESTRINGIDA");
		for (String role : new String[] {"GERENTE", "ANALISTA"})
			try (XSSFWorkbook book =
					new XSSFWorkbook(new ByteArrayInputStream(generate(role, "XLSX")))) {
				if (role.equals("GERENTE")) assertThat(book.getSheet("Excluidas")).isNull();
				else assertThat(book.getSheet("Excluidas")).isNotNull();
				StringBuilder cells = new StringBuilder();
				for (var sheet : book)
					for (var row : sheet)
						for (var cell : row) cells.append(cell.toString()).append('\n');
				if (role.equals("GERENTE"))
					assertThat(cells.toString())
							.doesNotContain(
									"Pendientes de vincular (sesiones)",
									"Energía rechazada",
									"REJECTED",
									"QA004 HISTORIA OPERATIVA RESTRINGIDA");
				assertThat(cells.toString())
						.contains("QA004 aprobación oficial vigente", "APPROVED");
			}
	}
}
