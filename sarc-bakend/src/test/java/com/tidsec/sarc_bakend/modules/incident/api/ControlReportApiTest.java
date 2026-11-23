package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentReportQuery;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentReportSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

import jakarta.servlet.http.Cookie;

/**
 * «Control de tickets» (US6, FR-070 a FR-077, FR-074a, FR-086): descarga para los cinco roles, estado al final del
 * periodo (escenarios 27 a 30), correccion despues de emitir con reconstruccion por huella (escenario 31), informe vacio
 * (escenario 32) y las mismas filas en el listado, el PDF y el Excel. Las fechas se calculan respecto de hoy para que la
 * prueba no caduque: «M1» es el mes de hace dos meses y «M2» el mes pasado.
 */
class ControlReportApiTest extends IncidentReportSupport {

	private static final Pattern PDF_ROW = Pattern.compile("^\\s*(TICKET-\\d+) .*?(Abierto|Cerrado)\\s*$", Pattern.MULTILINE);

	private static final LocalDate THIS_MONTH = LocalDate.now(ZONE).withDayOfMonth(1);
	private static final LocalDate M1 = THIS_MONTH.minusMonths(2);
	private static final LocalDate M2 = THIS_MONTH.minusMonths(1);

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	IncidentReportQuery reportQuery;

	// ----- Llamadas -----

	private MvcResult download(Cookie session, String format, String from, String to, String extra) throws Exception {
		String range = from == null ? "" : "&from=" + from + "&to=" + to;
		return get(session, "/reports/control?format=" + format + range + extra).andExpect(status().isOk()).andReturn();
	}

	private static List<String> pdfRows(byte[] pdf) throws Exception {
		try (var reader = new PdfReader(pdf)) {
			StringBuilder text = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				text.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
			}
			List<String> rows = new ArrayList<>();
			Matcher matcher = PDF_ROW.matcher(text);
			while (matcher.find()) {
				rows.add(matcher.group(1) + "|" + matcher.group(2));
			}
			return rows;
		}
	}

	private static String pdfText(byte[] pdf) throws Exception {
		try (var reader = new PdfReader(pdf)) {
			return new PdfTextExtractor(reader).getTextFromPage(1);
		}
	}

	private static List<String> excelRows(byte[] xlsx) throws Exception {
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			Sheet sheet = book.getSheet("Control");
			DataFormatter formatter = new DataFormatter();
			List<String> rows = new ArrayList<>();
			for (int row = 5; row <= sheet.getLastRowNum(); row++) {
				String code = formatter.formatCellValue(sheet.getRow(row).getCell(0));
				if (code.startsWith("TICKET-")) {
					rows.add(code + "|" + formatter.formatCellValue(sheet.getRow(row).getCell(2)));
				}
			}
			return rows;
		}
	}

	private List<String> listRows(String from, String to, String extra) throws Exception {
		String range = from == null ? "" : "&from=" + from + "&to=" + to;
		String body = get(analyst(), "/tickets?size=100&sort=code,asc" + range + extra).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();
		List<String> codes = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].code");
		List<String> states = com.jayway.jsonpath.JsonPath.read(body, from == null ? "$.items[*].state" : "$.items[*].stateAtPeriodEnd");
		List<String> rows = new ArrayList<>();
		for (int index = 0; index < codes.size(); index++) {
			rows.add(codes.get(index) + "|" + (states.get(index).equals("OPEN") ? "Abierto" : "Cerrado"));
		}
		return rows;
	}

	/** Las tres vistas del mismo informe, con los mismos filtros: el listado, el PDF y el Excel (FR-077). */
	private List<String> threeViews(String from, String to, String extra) throws Exception {
		List<String> pdf = pdfRows(download(analyst(), "PDF", from, to, extra).getResponse().getContentAsByteArray());
		List<String> excel = excelRows(download(analyst(), "XLSX", from, to, extra).getResponse().getContentAsByteArray());
		List<String> list = listRows(from, to, extra);
		assertThat(excel).as("el Excel tiene las filas del PDF").isEqualTo(pdf);
		assertThat(list).as("el listado tiene las filas del PDF").isEqualTo(pdf);
		return pdf;
	}

	private String audited(String reportId, String field) {
		return jdbc.queryForObject("SELECT details->>? FROM audit_event WHERE entity_id = ?::uuid AND action = 'INCIDENT_CONTROL_REPORT_GENERATED'",
				String.class, field, reportId);
	}

	// ----- Descarga y archivo -----

	@Test
	void everyRoleDownloadsBothFormatsAndEachIssueIsArchivedWithItsFingerprint() throws Exception {
		seed(9001, at(M2.plusDays(2)), null);

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			for (String format : List.of("PDF", "XLSX")) {
				MvcResult result = get(as(roles.toArray(Role[]::new)), "/reports/control?format=" + format)
						.andExpect(status().isOk()).andExpect(header().exists("X-Report-Id"))
						.andExpect(header().exists("X-Report-Cut-Off"))
						.andExpect(header().string("Cache-Control", "no-store, private")).andReturn();

				String id = result.getResponse().getHeader("X-Report-Id");
				byte[] body = result.getResponse().getContentAsByteArray();
				assertThat(jdbc.queryForObject("SELECT kind FROM report_issued WHERE id = ?::uuid", String.class, id))
						.isEqualTo("INCIDENT_CONTROL");
				assertThat(jdbc.queryForObject("SELECT checksum FROM report_issued WHERE id = ?::uuid", String.class, id))
						.isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
				assertThat(audited(id, "rowsSha256")).hasSize(64);
				assertThat(audited(id, "rowCount")).isEqualTo("1");
			}
		}
	}

	@Test
	void aRangeOfMoreThan366DaysIsRejectedAndAHalfRangeToo() throws Exception {
		assertProblem(get(analyst(), "/reports/control?format=PDF&from=" + iso(THIS_MONTH.minusDays(400)) + "&to=" + iso(THIS_MONTH)),
				422, "PERIOD_TOO_LONG");
		assertProblem(get(analyst(), "/reports/control?format=PDF&from=" + iso(THIS_MONTH)), 422, "PERIOD_INVALID");
		assertProblem(get(analyst(), "/reports/control?format=PDF&from=" + iso(M2) + "&to=" + iso(M1)), 422, "PERIOD_INVALID");
		get(analyst(), "/reports/control?format=PDF&from=" + iso(THIS_MONTH.minusDays(365)) + "&to=" + iso(THIS_MONTH))
				.andExpect(status().isOk());
	}

	// ----- Estado al final del periodo (escenarios 27 a 30) -----

	@Test
	void scenario27AnOctoberTicketClosedInNovemberIsOpenInTheFirstMonthAndClosedAfterward() throws Exception {
		seed(9001, at(M1.plusDays(24)), at(M2.plusDays(2)));

		assertThat(threeViews(iso(M1), iso(last(M1)), "")).containsExactly("TICKET-9001|Abierto");
		assertThat(threeViews(iso(M2), iso(last(M2)), "")).containsExactly("TICKET-9001|Cerrado");
		assertThat(threeViews(null, null, "")).as("todo el historial").containsExactly("TICKET-9001|Cerrado");
	}

	@Test
	void scenario28AReopenedTicketAppearsOnceWithTheStateOfTheEpisodeInForce() throws Exception {
		UUID ticket = seed(9001, at(M1.plusDays(14)), at(M1.plusDays(19)));
		addEpisode(ticket, 2, at(M2.plusDays(4)), null);
		jdbc.update("UPDATE inc_ticket SET state = 'OPEN' WHERE id = ?", ticket);

		assertThat(threeViews(iso(M1), iso(last(M1)), "")).as("fin del primer mes: el episodio 1 ya cerro")
				.containsExactly("TICKET-9001|Cerrado");
		assertThat(threeViews(iso(M2), iso(last(M2)), "")).as("segundo mes: una sola fila, abierta por el episodio 2")
				.containsExactly("TICKET-9001|Abierto");
	}

	@Test
	void scenario29AClosureExactlyAtTheExclusiveEndKeepsTheTicketOpenInThatPeriod() throws Exception {
		seed(9001, at(M1.plusDays(9)), at(M2));

		assertThat(threeViews(iso(M1), iso(last(M1)), "")).containsExactly("TICKET-9001|Abierto");
		assertThat(threeViews(iso(M2), iso(last(M2)), "")).containsExactly("TICKET-9001|Cerrado");
	}

	@Test
	void scenario30APeriodThatHasNotEndedUsesTheCutOff() throws Exception {
		Instant today = at(LocalDate.now(ZONE));
		Instant tomorrowMorning = Instant.now().plusSeconds(2 * 3600);
		seed(9001, today, null);
		seed(9002, today, today.plusSeconds(1));
		seed(9003, today, tomorrowMorning);

		assertThat(threeViews(iso(THIS_MONTH), iso(last(THIS_MONTH)), "")).containsExactly("TICKET-9001|Abierto",
				"TICKET-9002|Cerrado", "TICKET-9003|Abierto");
	}

	@Test
	void theStateAndNetworkFiltersApplyToTheStateAtTheEndOfThePeriodAndAreTheSameInEveryView() throws Exception {
		seed(9001, at(M1.plusDays(24)), at(M2.plusDays(2)));
		seed(9002, at(M1.plusDays(5)), at(M1.plusDays(6)));

		assertThat(threeViews(iso(M1), iso(last(M1)), "&state=OPEN")).containsExactly("TICKET-9001|Abierto");
		assertThat(threeViews(iso(M1), iso(last(M1)), "&state=CLOSED")).containsExactly("TICKET-9002|Cerrado");
		assertThat(threeViews(iso(M1), iso(last(M1)), "&network=JAC")).as("insertEquipmentTicket usa KIA")
				.isEmpty();
		assertThat(threeViews(iso(M1), iso(last(M1)), "&network=KIA")).hasSize(2);
	}

	// ----- Corregir despues de emitir (escenario 31) -----

	@Test
	void scenario31ACorrectionAfterIssuingDoesNotChangeTheIssuedReportAndItsRowsCanBeRebuilt() throws Exception {
		UUID ticket = seed(9001, at(M1.plusDays(24)), at(M2.plusDays(2)));

		MvcResult first = download(analyst(), "PDF", iso(M1), iso(last(M1)), "");
		String firstId = first.getResponse().getHeader("X-Report-Id");
		Instant cutOffOne = Instant.parse(first.getResponse().getHeader("X-Report-Cut-Off"));
		String checksumOne = jdbc.queryForObject("SELECT checksum FROM report_issued WHERE id = ?::uuid", String.class, firstId);
		String rowsOne = audited(firstId, "rowsSha256");
		assertThat(pdfRows(first.getResponse().getContentAsByteArray())).containsExactly("TICKET-9001|Abierto");

		Thread.sleep(25);
		put(analyst(), "/tickets/" + ticket + "/episodes/1/milestones/T4",
				"{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\",\"justification\":\"El cierre fue en octubre\"}"
						.formatted(versionOf(ticket), at(M1.plusDays(27)))).andExpect(status().isOk());

		MvcResult second = download(analyst(), "PDF", iso(M1), iso(last(M1)), "");
		assertThat(pdfRows(second.getResponse().getContentAsByteArray())).as("el informe nuevo ya refleja la correccion")
				.containsExactly("TICKET-9001|Cerrado");
		assertThat(audited(second.getResponse().getHeader("X-Report-Id"), "rowsSha256")).isNotEqualTo(rowsOne);

		assertThat(jdbc.queryForObject("SELECT checksum FROM report_issued WHERE id = ?::uuid", String.class, firstId))
				.as("el primer informe conserva su huella").isEqualTo(checksumOne);
		var selection = new IncidentReportQuery.Selection(M1, last(M1), null, null);
		// La consulta exige un rol de lectura: la reconstruccion la hace un lector, no una solicitud HTTP.
		org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.TestingAuthenticationToken("lector", "x", "ROLE_ANALISTA"));
		IncidentReportQuery.ControlData rebuilt;
		try {
			rebuilt = new TransactionTemplate(transactions).execute(status -> reportQuery.control(selection, cutOffOne));
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
		assertThat(rebuilt.rows()).extracting(IncidentReportQuery.ControlRow::code).containsExactly("TICKET-9001");
		assertThat(IncidentReportQuery.rowsSha256(rebuilt.rows())).as("MilestonesAsKnownAt(corte1) reproduce las filas del primero")
				.isEqualTo(rowsOne);
	}

	// ----- Vacio (escenario 32) -----

	@Test
	void scenario32AnEmptyPeriodStillProducesBothFilesWithTheHeaderAndTheMessage() throws Exception {
		LocalDate empty = THIS_MONTH.minusMonths(8);

		byte[] pdf = download(analyst(), "PDF", iso(empty), iso(last(empty)), "").getResponse().getContentAsByteArray();
		assertThat(pdfText(pdf)).contains("CONTROL DE TICKETS", "REGISTRO OPERATIVO", "Sin tickets en el período");
		byte[] xlsx = download(analyst(), "XLSX", iso(empty), iso(last(empty)), "").getResponse().getContentAsByteArray();
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(new DataFormatter().formatCellValue(book.getSheet("Control").getRow(5).getCell(0)))
					.isEqualTo("Sin tickets en el período");
		}
		get(analyst(), "/tickets?from=" + iso(empty) + "&to=" + iso(last(empty))).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void theListWithoutAPeriodDoesNotCarryTheStateAtPeriodEnd() throws Exception {
		seed(9001, at(M2.plusDays(2)), null);

		get(analyst(), "/tickets").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].stateAtPeriodEnd").doesNotExist());
	}

	@Test
	void withAPeriodTheCurrentConditionFilterIsIgnoredAndTheListStillAnswers() throws Exception {
		seed(9001, at(M1.plusDays(24)), at(M2.plusDays(2)));

		get(analyst(), "/tickets?condition=IN_ATTENTION&from=" + iso(M1) + "&to=" + iso(last(M1))).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].stateAtPeriodEnd").value("OPEN"));
		get(analyst(), "/tickets?condition=IN_ATTENTION").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
	}
}
