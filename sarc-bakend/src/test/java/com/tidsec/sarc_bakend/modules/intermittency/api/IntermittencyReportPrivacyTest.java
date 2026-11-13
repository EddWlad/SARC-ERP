package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.shared.audit.ContentFingerprint;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

import jakarta.servlet.http.Cookie;

/** El informe segun el rol, su archivo y que una correccion posterior no lo toque (FR-005, FR-040). */
class IntermittencyReportPrivacyTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;
	private static final String OCTOBER = "preset=CUSTOM&from=2026-10-01&to=2026-11-01";

	private final Map<String, byte[]> stored = new HashMap<>();
	private final AtomicInteger references = new AtomicInteger();

	@BeforeEach
	void archiveInMemory() {
		stored.clear();
		when(fileStorage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> {
			String reference = "report-ref-" + references.incrementAndGet();
			stored.put(reference, invocation.getArgument(2));
			return reference;
		});
		when(fileStorage.readDocument(anyString())).thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));
	}

	private void seed() {
		insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		insertWindow(POINT, at("2026-10-09", "08:00"), at("2026-10-09", "13:00"), "REVIEW");
		insertWindow(POINT, at("2026-10-30", "08:00"), null, "OPEN");
	}

	private String issue(Cookie session, String format, String query) throws Exception {
		return get(session, "/report?format=" + format + "&" + query).andExpect(status().isOk()).andReturn().getResponse()
				.getHeader("X-Report-Id");
	}

	private byte[] body(Cookie session, String format, String query) throws Exception {
		return get(session, "/report?format=" + format + "&" + query).andReturn().getResponse().getContentAsByteArray();
	}

	/** El historial de informes emitidos vive fuera de /intermittency: se pide con la ruta completa. */
	private org.springframework.test.web.servlet.ResultActions reports(Cookie session, String path) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/reports/issued" + path).cookie(session));
	}

	private String profile(String id) {
		return jdbc.queryForObject("SELECT content_profile FROM report_issued WHERE id = ?::uuid", String.class, id);
	}

	private static String pdfText(byte[] bytes) throws Exception {
		try (var reader = new PdfReader(bytes)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append(' ');
			}
			return all.toString().replaceAll("\\s+", " ");
		}
	}

	private static int outsideRows(byte[] excel) throws Exception {
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			int[] rows = { 0 };
			boolean[] inTable = { false };
			book.getSheet("Fuera de oficiales").forEach(row -> {
				if (inTable[0]) {
					rows[0]++;
				}
				else {
					inTable[0] = row.getCell(0) != null && "Concepto".equals(row.getCell(0).getStringCellValue());
				}
			});
			return rows[0];
		}
	}

	@Test
	void whoDoesNotEditReceivesAnOfficialOnlyReportAndAnEditorOneWithOperations() throws Exception {
		seed();

		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			String pdf = issue(as(role), "PDF", OCTOBER);
			assertThat(profile(pdf)).isEqualTo("OFFICIAL_ONLY");
			assertThat(pdfText(body(as(role), "PDF", OCTOBER))).doesNotContain("Anexo:").contains("Eventos abiertos");
			assertThat(outsideRows(body(as(role), "XLSX", OCTOBER))).isEqualTo(6);
		}
		String editor = issue(analyst(), "PDF", OCTOBER);
		assertThat(profile(editor)).isEqualTo("WITH_OPERATIONS");
		assertThat(pdfText(body(analyst(), "PDF", OCTOBER))).contains("Anexo: ventanas fuera de lo oficial");
		assertThat(outsideRows(body(analyst(), "XLSX", OCTOBER))).isGreaterThan(6);
		// Quien combina un rol de consulta con uno de edicion recibe el informe de editor.
		assertThat(profile(issue(as(Role.ANALISTA, Role.GERENTE), "XLSX", OCTOBER))).isEqualTo("WITH_OPERATIONS");
		assertThat(profile(issue(as(Role.ADMINISTRADOR), "XLSX", OCTOBER))).isEqualTo("WITH_OPERATIONS");
	}

	@Test
	void anArchivedReportWithOperationsIsBlockedForAReadOnlyRoleWithTheOptionToIssueANewOne() throws Exception {
		seed();
		String restricted = issue(analyst(), "PDF", OCTOBER);
		String official = issue(as(Role.GERENTE), "PDF", OCTOBER);

		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			reports(as(role), "/" + restricted + "/download").andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("REPORT_OPERATIONAL_CONTENT_RESTRICTED"))
					.andExpect(jsonPath("$.action").value("ISSUE_MANAGERIAL_REPORT"));
		}
		// La copia restringida nunca se abrio para ellos.
		String restrictedReference = jdbc.queryForObject("SELECT file_ref FROM report_issued WHERE id = ?::uuid",
				String.class, restricted);
		verify(fileStorage, never()).readDocument(restrictedReference);
		reports(as(Role.GERENTE), "/" + official + "/download").andExpect(status().isOk());
		reports(analyst(), "/" + restricted + "/download").andExpect(status().isOk());
		reports(as(Role.GERENTE), "").andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id=='" + restricted + "')].canDownload").value(false))
				.andExpect(jsonPath("$.content[?(@.id=='" + restricted + "')].kind").value("INTERMITTENCY"));
	}

	@Test
	void aLaterCorrectionDoesNotChangeTheArchivedReportAndItsCriteriaAndFingerprintAreKept() throws Exception {
		UUID window = insertWindow(POINT, at("2026-10-05", "08:00"), at("2026-10-05", "08:30"), "CONFIRMED");
		String query = OCTOBER + "&equipmentIds=" + POINT;
		String id = issue(analyst(), "XLSX", query);
		String reference = jdbc.queryForObject("SELECT file_ref FROM report_issued WHERE id = ?::uuid", String.class, id);
		byte[] original = stored.get(reference).clone();
		String checksum = jdbc.queryForObject("SELECT checksum FROM report_issued WHERE id = ?::uuid", String.class, id);

		patchAs(analyst(), "/windows/" + window, "{\"version\":0,\"endAt\":\"%s\",\"justification\":\"Fin real\"}"
				.formatted(at("2026-10-05", "09:00"))).andExpect(status().isOk());

		assertThat(stored.get(reference)).isEqualTo(original);
		assertThat(ContentFingerprint.sha256(original)).isEqualTo(checksum);
		byte[] downloaded = reports(analyst(), "/" + id + "/download").andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(downloaded).isEqualTo(original);
		// Un informe nuevo si refleja la correccion: son documentos distintos.
		assertThat(ContentFingerprint.sha256(body(analyst(), "XLSX", query))).isNotEqualTo(checksum);

		String criteria = jdbc.queryForObject("SELECT criteria::text FROM report_issued WHERE id = ?::uuid", String.class,
				id);
		assertThat(criteria).contains("\"from\": \"2026-10-01\"").contains("\"to\": \"2026-11-01\"")
				.contains("\"periodPreset\": \"CUSTOM\"").contains("\"timeZone\": \"America/Guayaquil\"")
				.contains("\"equipmentIds\": \"" + POINT + "\"");
		assertThat(jdbc.queryForObject("SELECT kind FROM report_issued WHERE id = ?::uuid", String.class, id))
				.isEqualTo("INTERMITTENCY");
		assertThat(jdbc.queryForObject("SELECT cut_off IS NOT NULL FROM report_issued WHERE id = ?::uuid", Boolean.class, id))
				.isTrue();
	}

	@Test
	void aFilterWithAnEquipmentOutsideTheScopeNeverNamesItInTheReportOrItsArchivedScope() throws Exception {
		seed();
		String query = OCTOBER + "&equipmentIds=" + Feature008Fixtures.KIA_STATION + "&equipmentIds="
				+ Feature008Fixtures.JAC_POINT + "&equipmentIds=" + POINT;

		String id = issue(as(Role.GERENTE), "PDF", query);

		String scope = jdbc.queryForObject("SELECT scope FROM report_issued WHERE id = ?::uuid", String.class, id);
		assertThat(scope).contains("no disponible").doesNotContain("QA008 KIA Electrolinera")
				.doesNotContain("QA008 JAC Punto");
		String text = pdfText(body(as(Role.GERENTE), "PDF", query));
		assertThat(text).doesNotContain("QA008 KIA Electrolinera").doesNotContain("QA008 JAC Punto");
	}

	@Test
	void theEmissionIsAuditedWithItsFingerprintAndWithoutFigures() throws Exception {
		seed();
		String id = issue(analyst(), "PDF", OCTOBER);

		String details = jdbc.queryForObject("""
				SELECT details::text FROM audit_event WHERE action = 'INTERMITTENCY_REPORT_GENERATED'
				AND entity_id = ?::uuid
				""", String.class, id);

		assertThat(details).contains("sha256").contains("\"format\": \"PDF\"").doesNotContain("minutes");
	}

	@Test
	void theAvailabilityOnlyReachesTheReportOfWhoHasThePermissionAndItsArchivedCopyIsBlockedForTheOthers() throws Exception {
		seed();

		String editor = issue(analyst(), "PDF", OCTOBER);
		String reader = issue(as(Role.GERENTE), "PDF", OCTOBER);
		String combined = issue(as(Role.ANALISTA, Role.GERENTE), "XLSX", OCTOBER);

		assertThat(pdfText(body(analyst(), "PDF", OCTOBER))).contains("Disponibilidad estimada (prueba)");
		assertThat(pdfText(body(as(Role.GERENTE), "PDF", OCTOBER))).doesNotContain("Disponibilidad estimada");
		assertThat(sheetNames(body(analyst(), "XLSX", OCTOBER))).contains("Disponibilidad");
		assertThat(sheetNames(body(as(Role.GERENTE), "XLSX", OCTOBER))).doesNotContain("Disponibilidad");
		assertThat(sheetNames(body(as(Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE), "XLSX", OCTOBER)))
				.doesNotContain("Disponibilidad");
		assertThat(sheetNames(body(as(Role.ADMINISTRADOR, Role.ASISTENTE), "XLSX", OCTOBER))).contains("Disponibilidad");
		assertThat(profile(editor)).isEqualTo("WITH_OPERATIONS");
		assertThat(profile(combined)).isEqualTo("WITH_OPERATIONS");
		assertThat(profile(reader)).isEqualTo("OFFICIAL_ONLY");
		// La copia archivada con disponibilidad no se entrega a un rol de consulta; la oficial si.
		for (Role role : new Role[] { Role.GERENTE, Role.SUPERVISOR, Role.ASISTENTE }) {
			reports(as(role), "/" + editor + "/download").andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("REPORT_OPERATIONAL_CONTENT_RESTRICTED"));
			reports(as(role), "/" + reader + "/download").andExpect(status().isOk());
		}
	}

	private static java.util.List<String> sheetNames(byte[] excel) throws Exception {
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			java.util.List<String> names = new java.util.ArrayList<>();
			book.forEach(sheet -> names.add(sheet.getSheetName()));
			return names;
		}
	}
}
