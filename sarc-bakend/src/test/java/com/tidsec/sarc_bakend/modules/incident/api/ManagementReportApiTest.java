package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.modules.identity.domain.model.Role;
import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.application.service.IncidentManagementQuery;
import com.tidsec.sarc_bakend.modules.incident.application.service.ManagementReportService;
import com.tidsec.sarc_bakend.modules.incident.support.IncidentReportSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/**
 * Tablero y reporte de gestion (US7, FR-080 a FR-086): las cifras del tablero, del PDF y del Excel coinciden con el mismo
 * corte, los denominadores y las exclusiones son los de los escenarios 33 y 34, y no hay cifras de disponibilidad
 * (escenario 39). Las fechas se calculan respecto de hoy; «M2» es el mes pasado.
 */
class ManagementReportApiTest extends IncidentReportSupport {

	private static final LocalDate THIS_MONTH = LocalDate.now(ZONE).withDayOfMonth(1);
	private static final LocalDate M2 = THIS_MONTH.minusMonths(1);

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	IncidentManagementQuery query;

	/** Cinco episodios de M2: tres cerrados completos, uno con «No aplica» y otro con «Sin dato», uno abierto y un duplicado. */
	private void fixture() {
		UUID a = seed(9001, at(M2.plusDays(4)), at(M2.plusDays(4)).plusSeconds(90 * 60));
		milestones(a, 4, 10, 60);
		UUID b = seed(9002, at(M2.plusDays(5)), at(M2.plusDays(5)).plusSeconds(150 * 60));
		milestones(b, 6, -1, 120);
		setMilestone(b, 1, "T2", "NOT_APPLICABLE", null);
		UUID c = seed(9003, at(M2.plusDays(6)), at(M2.plusDays(6)).plusSeconds(100 * 60));
		milestones(c, -1, 20, 70);
		setMilestone(c, 1, "T1", "NO_DATA", null);
		UUID d = seed(9004, at(M2.plusDays(7)), null);
		milestones(d, 3, -1, -1);
		UUID e = seed(9005, at(M2.plusDays(8)), at(M2.plusDays(8)).plusSeconds(600));
		jdbc.update("UPDATE inc_episode SET closure_result = 'DUPLICATE', duplicate_of_ticket_id = ? WHERE ticket_id = ?", a, e);
	}

	/** T1, T2 y T3 en minutos despues de T0; -1 los deja pendientes. */
	private void milestones(UUID ticket, int t1, int t2, int t3) {
		Instant t0 = jdbc.queryForObject("""
				SELECT m.occurred_at FROM inc_milestone m JOIN inc_episode e ON e.id = m.episode_id
				WHERE e.ticket_id = ? AND e.ordinal = 1 AND m.kind = 'T0'""", java.sql.Timestamp.class, ticket).toInstant();
		int[] minutes = { t1, t2, t3 };
		String[] kinds = { "T1", "T2", "T3" };
		for (int index = 0; index < 3; index++) {
			if (minutes[index] >= 0) {
				setMilestone(ticket, 1, kinds[index], "RECORDED", t0.plusSeconds(minutes[index] * 60L));
			}
		}
	}

	/** Un campo de la medida con ese nombre, leido del JSON del tablero. */
	private static <T> T first(String json, String pair, String field, Class<T> type) {
		List<Object> values = JsonPath.read(json, "$.measures[?(@.pair=='" + pair + "')]." + field);
		return type.cast(values.get(0));
	}

	private String period(String extra) {
		return "from=" + iso(M2) + "&to=" + iso(last(M2)) + extra;
	}

	private String summaryJson(String extra) throws Exception {
		return get(analyst(), "/summary?" + period(extra)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private MvcResult report(String format, String extra) throws Exception {
		return get(analyst(), "/reports/management?format=" + format + "&" + period(extra)).andExpect(status().isOk())
				.andReturn();
	}

	private static String pdfText(byte[] pdf) throws Exception {
		try (var reader = new PdfReader(pdf)) {
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(new PdfTextExtractor(reader).getTextFromPage(page)).append('\n');
			}
			return all.toString();
		}
	}

	private static String cell(Row row, int column) {
		return row == null || row.getCell(column) == null ? "" : new DataFormatter().formatCellValue(row.getCell(column));
	}

	/** La fila de {@code Resumen} con ese indicador: valor, denominador y excluidos. */
	private static List<String> summaryRow(Sheet summary, String indicator) {
		for (int index = 5; index <= summary.getLastRowNum(); index++) {
			if (indicator.equals(cell(summary.getRow(index), 0))) {
				return List.of(cell(summary.getRow(index), 1).replace(',', '.'), cell(summary.getRow(index), 2),
						cell(summary.getRow(index), 3));
			}
		}
		throw new AssertionError("Falta el indicador " + indicator);
	}

	// ----- Tablero (T098) -----

	@Test
	void scenario33EveryAverageCarriesItsDenominatorAndTheExclusionsAreCountedApart() throws Exception {
		fixture();

		String json = summaryJson("");

		assertThat(JsonPath.<Integer>read(json, "$.totals.uniqueTickets")).isEqualTo(5);
		assertThat(JsonPath.<Integer>read(json, "$.totals.episodes")).isEqualTo(5);
		assertThat(JsonPath.<Integer>read(json, "$.totals.openEpisodes")).isEqualTo(1);
		assertThat(JsonPath.<Integer>read(json, "$.totals.closedEpisodes")).isEqualTo(3);
		assertThat(JsonPath.<Integer>read(json, "$.totals.excluded")).as("duplicado, aparte").isEqualTo(1);
		assertThat(JsonPath.<Integer>read(json, "$.openTicketsAtCutOff")).isEqualTo(1);
		assertThat(first(json, "T1-T0", "n", Integer.class)).isEqualTo(3);
		assertThat(first(json, "T1-T0", "averageMinutes", Double.class)).isEqualTo(4.3);
		assertThat(first(json, "T1-T0", "noData", Integer.class)).isEqualTo(1);
		assertThat(first(json, "T2-T0", "n", Integer.class)).isEqualTo(2);
		assertThat(first(json, "T2-T0", "notApplicable", Integer.class)).isEqualTo(1);
		assertThat(first(json, "T2-T0", "open", Integer.class)).as("el abierto").isEqualTo(1);
		assertThat(first(json, "T3-T0", "averageMinutes", Double.class)).isEqualTo(83.3);
		assertThat(JsonPath.<Integer>read(json, "$.validation.n")).isEqualTo(3);
		assertThat(JsonPath.<Integer>read(json, "$.validation.within")).isEqualTo(2);
		assertThat(JsonPath.<Double>read(json, "$.validation.percent")).isEqualTo(66.7);
		assertThat(JsonPath.<Integer>read(json, "$.byNetwork[0].count")).isEqualTo(4);
	}

	@Test
	void everyRoleReadsTheSummaryAndItNeverCarriesAvailabilityFigures() throws Exception {
		fixture();

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			String json = get(as(roles.toArray(Role[]::new)), "/summary?" + period("")).andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			assertThat(json).doesNotContainIgnoringCase("availab").doesNotContainIgnoringCase("disponib");
			assertThat(JsonPath.<Integer>read(json, "$.totals.episodes")).isEqualTo(5);
		}
	}

	@Test
	void theFiltersNarrowTheFiguresAndAnEmptyPeriodGivesZerosWithNoAverages() throws Exception {
		fixture();

		assertThat(JsonPath.<Integer>read(summaryJson("&priority=CRITICAL"), "$.totals.episodes")).isZero();
		assertThat(JsonPath.<Integer>read(summaryJson("&network=JAC"), "$.totals.episodes")).isZero();
		assertThat(JsonPath.<Integer>read(summaryJson("&network=KIA"), "$.totals.episodes")).isEqualTo(5);
		assertThat(JsonPath.<Integer>read(summaryJson("&incidentType=DISCONNECTION&elementClass=EQUIPMENT"),
				"$.totals.episodes")).isEqualTo(5);

		LocalDate empty = THIS_MONTH.minusMonths(8);
		String json = get(analyst(), "/summary?from=" + iso(empty) + "&to=" + iso(last(empty))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(json, "$.totals.episodes")).isZero();
		assertThat(JsonPath.<List<Integer>>read(json, "$.measures[*].n")).containsOnly(0);
		assertThat(json).doesNotContain("\"averageMinutes\":0");
	}

	@Test
	void thePeriodIsRequiredAndLimitedTo366Days() throws Exception {
		get(analyst(), "/summary").andExpect(status().isBadRequest());
		assertProblem(get(analyst(), "/summary?from=" + iso(THIS_MONTH.minusDays(400)) + "&to=" + iso(THIS_MONTH)), 422,
				"PERIOD_TOO_LONG");
		assertProblem(get(analyst(), "/summary?from=" + iso(M2) + "&to=" + iso(M2.minusDays(3))), 422, "PERIOD_INVALID");
	}

	// ----- Reporte y coherencia (T099, T100) -----

	@Test
	void scenario34TheSummaryThePdfAndTheExcelAgreeFigureByFigureAndTheIssueIsArchived() throws Exception {
		fixture();
		String json = summaryJson("");
		MvcResult excelResult = report("XLSX", "");
		MvcResult pdfResult = report("PDF", "");

		try (var book = new XSSFWorkbook(new ByteArrayInputStream(excelResult.getResponse().getContentAsByteArray()))) {
			assertThat(book.getSheetName(0)).isEqualTo("Resumen");
			List<String> names = new ArrayList<>();
			book.forEach(sheet -> names.add(sheet.getSheetName()));
			assertThat(names).containsExactly("Resumen", "Tickets", "Episodios", "Bitácora", "Comunicaciones", "Visitas",
					"Metodología");
			Sheet summary = book.getSheet("Resumen");
			assertThat(summaryRow(summary, "Episodios").get(0)).isEqualTo(String.valueOf(JsonPath.<Integer>read(json, "$.totals.episodes")));
			assertThat(summaryRow(summary, "Tickets únicos").get(0)).isEqualTo("5");
			assertThat(summaryRow(summary, "Cerrados por duplicado o error").get(0)).isEqualTo("1");
			assertThat(summaryRow(summary, "Promedio Primera validación (T1−T0) (min)").subList(0, 2)).containsExactly("4.3", "3");
			assertThat(summaryRow(summary, "Validaciones dentro de la referencia (%)").subList(0, 2)).containsExactly("66.7", "3");

			// FR-085: cada cifra de Resumen se reproduce con un conteo o un promedio sobre Episodios.
			Sheet episodes = book.getSheet("Episodios");
			int count = 0;
			int sum = 0;
			int open = 0;
			for (int index = 5; index <= episodes.getLastRowNum(); index++) {
				String closure = cell(episodes.getRow(index), 25);
				if (closure.equals("DUPLICATE") || closure.equals("REGISTERED_IN_ERROR")) {
					continue;
				}
				count++;
				if (cell(episodes.getRow(index), 6).equals("Abierto")) {
					open++;
				}
				String minutes = cell(episodes.getRow(index), 17);
				if (!minutes.isEmpty()) {
					sum += Integer.parseInt(minutes);
				}
			}
			assertThat(count).isEqualTo(4);
			assertThat(open).isEqualTo(Integer.parseInt(summaryRow(summary, "Episodios abiertos").get(0)));
			assertThat(Math.round(sum * 10.0 / 3) / 10.0).isEqualTo(4.3);
		}

		String pdf = pdfText(pdfResult.getResponse().getContentAsByteArray());
		assertThat(pdf).contains("Gestión de incidencias", "Resumen ejecutivo", "Indicadores de tiempo",
				"Referencias operativas", "Referencia operativa, no SLA contractual", "Gráficos (sin ponderar)", "Repartos",
				"Visitas técnicas", "Detalle por ticket y episodio", "Fuentes externas", "Metodología");
		assertThat(pdf).contains("4,3").contains("83,3").contains("66,7 %");
		assertThat(pdf).contains("Uptime", "Intermitencias", "no se recalculan aquí");

		for (MvcResult result : List.of(excelResult, pdfResult)) {
			String id = result.getResponse().getHeader("X-Report-Id");
			result.getResponse().getHeader("X-Report-Cut-Off");
			assertThat(jdbc.queryForObject("SELECT kind FROM report_issued WHERE id = ?::uuid", String.class, id))
					.isEqualTo("INCIDENT_MANAGEMENT");
			assertThat(jdbc.queryForObject("SELECT details->>'rowsSha256' FROM audit_event WHERE entity_id = ?::uuid"
					+ " AND action = 'INCIDENT_MANAGEMENT_REPORT_GENERATED'", String.class, id)).hasSize(64);
		}
		excelResultHeaders(excelResult);
	}

	private static void excelResultHeaders(MvcResult result) {
		assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store, private");
	}

	@Test
	void theRowsOfAnIssuedReportCanBeRebuiltAfterALaterCorrection() throws Exception {
		fixture();
		MvcResult first = report("XLSX", "");
		String id = first.getResponse().getHeader("X-Report-Id");
		java.time.Instant cutOff = java.time.Instant.parse(first.getResponse().getHeader("X-Report-Cut-Off"));
		String issued = jdbc.queryForObject("SELECT details->>'rowsSha256' FROM audit_event WHERE entity_id = ?::uuid"
				+ " AND action = 'INCIDENT_MANAGEMENT_REPORT_GENERATED'", String.class, id);

		UUID ticket = jdbc.queryForObject("SELECT id FROM inc_ticket WHERE ticket_number = 9001", UUID.class);
		Thread.sleep(25);
		put(analyst(), "/tickets/" + ticket + "/episodes/1/milestones/T1",
				"{\"version\":%d,\"status\":\"RECORDED\",\"occurredAt\":\"%s\",\"reason\":\"Hora real\",\"justification\":\"Error de captura\"}"
						.formatted(versionOf(ticket), at(M2.plusDays(4)).plusSeconds(9 * 60))).andExpect(status().isOk());

		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("lector", "x", "ROLE_ANALISTA"));
		try {
			var selection = new IncidentManagementQuery.Selection(M2, last(M2), null, null, null, null);
			ManagementData rebuilt = new TransactionTemplate(transactions).execute(status -> query.management(selection, cutOff));
			ManagementData now = new TransactionTemplate(transactions)
					.execute(status -> query.management(selection, java.time.Instant.now()));
			assertThat(ManagementReportService.rowsSha256(rebuilt)).as("el corte 1 reproduce las filas emitidas").isEqualTo(issued);
			assertThat(ManagementReportService.rowsSha256(now)).as("hoy ya ve la correccion").isNotEqualTo(issued);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void everyRoleDownloadsBothFormats() throws Exception {
		fixture();

		for (Set<Role> roles : Feature010Fixtures.ROLE_SETS) {
			for (String format : List.of("PDF", "XLSX")) {
				get(as(roles.toArray(Role[]::new)), "/reports/management?format=" + format + "&" + period(""))
						.andExpect(status().isOk()).andExpect(header().exists("X-Report-Id"))
						.andExpect(header().string("Cache-Control", "no-store, private"));
			}
		}
		get(analyst(), "/reports/management?format=PDF").andExpect(status().isBadRequest());
		get(analyst(), "/summary?" + period("")).andExpect(jsonPath("$.totals.episodes").value(5));
	}
}
