package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature004Fixtures;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * Regresion de la Feature 008 (FR-047, FR-050, SC-008, SC-012): las cifras, rankings, coberturas e informes de
 * Consumo y de Uptime, y el listado de Inventario, son identicos antes y despues de que existan ventanas de
 * intermitencia, lotes, dias revisados e informes de intermitencias. Los puntos KIA de la 008 ya estan en el
 * inventario en las dos lecturas: lo que cambia entre ellas son solo las tablas {@code itm_*} y el archivo de
 * informes, que es lo que el modulo nuevo agrega.
 */
@SpringBootTest(properties = { "sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class Feature008RegressionBaseline {

	private static final String ENERGY_RANGE = "from=2026-10-01&to=2026-11-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	FileStorage storage;

	/** Lo que leen las pantallas de Consumo, Uptime e Inventario, ya sin horas de calculo. */
	private List<String> snapshot() throws Exception {
		List<String> lines = new ArrayList<>();
		for (String role : List.of("GERENTE", "ANALISTA")) {
			lines.add("## " + role + " energy summary");
			lines.add(text(fetch(role, "/api/v1/energy/summary?" + ENERGY_RANGE)));
			lines.add("## " + role + " energy PDF");
			lines.addAll(pdf(fetch(role, "/api/v1/energy/report?format=PDF&" + ENERGY_RANGE)));
			lines.add("## " + role + " energy XLSX");
			lines.addAll(excel(fetch(role, "/api/v1/energy/report?format=XLSX&" + ENERGY_RANGE)));
			for (String path : List.of("/summary", "/results/connectors", "/results/equipment", "/results/exclusions")) {
				lines.add("## " + role + " uptime" + path);
				lines.add(text(fetch(role, "/api/v1/uptime" + path)));
			}
			lines.add("## " + role + " equipment");
			lines.add(text(fetch(role, "/api/v1/equipment?size=100")));
		}
		return lines;
	}

	private byte[] fetch(String role, String url) throws Exception {
		return mvc.perform(get(url).with(user("qa").roles(role))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsByteArray();
	}

	private static String text(byte[] body) {
		return new String(body, java.nio.charset.StandardCharsets.UTF_8)
				.replaceAll("\"(cutOff|generatedAt|createdAt|updatedAt)\":\"[^\"]*\"", "\"$1\":\"-\"");
	}

	/** Datos propios del modulo nuevo: ventanas con su historial, un lote, dias revisados y un informe emitido. */
	private void addIntermittencyData() throws Exception {
		insertWindow(Feature008Fixtures.BLOCKED, "2026-10-05T08:00:00-05:00", "2026-10-05T09:12:00-05:00", "CONFIRMED");
		insertWindow(Feature008Fixtures.FREE, "2026-10-06T10:00:00-05:00", "2026-10-06T15:00:00-05:00", "REVIEW");
		insertWindow(Feature008Fixtures.SECOND_BLOCKED, "2026-10-07T08:00:00-05:00", null, "OPEN");
		UUID admin = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO itm_reviewed_day (day, active, marked_by, marked_at, version)
				VALUES ('2026-10-05', true, ?, now(), 0), ('2026-10-06', true, ?, now(), 0)
				""", admin, admin);
		jdbc.update("""
				INSERT INTO itm_reviewed_day_event (id, day, action, actor_id, at, pending_batches_warned)
				VALUES (?, '2026-10-05', 'MARKED', ?, now(), 0)
				""", UUID.randomUUID(), admin);
		for (String role : List.of("GERENTE", "ANALISTA")) {
			fetch(role, "/api/v1/intermittency/summary?preset=CUSTOM&from=2026-10-01&to=2026-11-01");
			fetch(role, "/api/v1/intermittency/report?format=PDF&preset=CUSTOM&from=2026-10-01&to=2026-11-01");
			fetch(role, "/api/v1/intermittency/report?format=XLSX&preset=CUSTOM&from=2026-10-01&to=2026-11-01");
		}
		fetch("ANALISTA", "/api/v1/intermittency/availability?preset=CUSTOM&from=2026-10-01&to=2026-11-01");
	}

	private void insertWindow(UUID point, String start, String end, String state) {
		UUID id = UUID.randomUUID();
		Timestamp from = Timestamp.from(OffsetDateTime.parse(start).toInstant());
		Timestamp to = end == null ? null : Timestamp.from(OffsetDateTime.parse(end).toInstant());
		Integer minutes = end == null ? null
				: (int) java.time.Duration.between(OffsetDateTime.parse(start), OffsetDateTime.parse(end)).toMinutes();
		String reasons = "REVIEW".equals(state) ? "[\"LONG_WINDOW\"]" : "[]";
		jdbc.update("""
				INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
				    review_reasons, source, current_revision, created_at, updated_at, version)
				VALUES (?, ?, 'QA008 Punto', ?, ?, ?, ?, ?::jsonb, 'MANUAL', 1, now(), now(), 0)
				""", id, point, from, to, minutes, state, reasons);
		jdbc.update("""
				INSERT INTO itm_window_revision (id, window_id, revision, start_at, end_at, duration_minutes, state,
				    review_reasons, change, actor_id, at)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?::jsonb, 'CREATED', ?, now())
				""", UUID.randomUUID(), id, from, to, minutes, state, reasons, UUID.randomUUID());
	}

	@Test
	void consumptionUptimeAndInventoryAreIdenticalBeforeAndAfterIntermittencyData() throws Exception {
		jdbc.execute("TRUNCATE itm_reviewed_day_event, itm_reviewed_day, itm_window_revision, itm_import_row, itm_window,"
				+ " itm_import_batch, energy_import_row, energy_session_revision, energy_session_review, energy_session,"
				+ " energy_import_batch, asset_equipment, report_issued CASCADE");
		Feature004Fixtures.seed(jdbc, false);
		Feature008Fixtures.seedInventory(jdbc);
		java.util.Map<String, byte[]> stored = new java.util.HashMap<>();
		when(storage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> {
			String reference = "qa008/report-" + stored.size();
			stored.put(reference, invocation.getArgument(2));
			return reference;
		});
		when(storage.readDocument(anyString())).thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));

		List<String> before = snapshot();
		addIntermittencyData();
		List<String> after = snapshot();

		assertThat(before).as("el snapshot no puede estar vacio").hasSizeGreaterThan(20);
		assertThat(after).isEqualTo(before);
		// El modulo nuevo archivo sus informes (PDF y Excel de dos roles) sin tocar los demas tipos.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM report_issued WHERE kind = 'INTERMITTENCY'", Integer.class))
				.isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM energy_session", Integer.class)).isPositive();
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
