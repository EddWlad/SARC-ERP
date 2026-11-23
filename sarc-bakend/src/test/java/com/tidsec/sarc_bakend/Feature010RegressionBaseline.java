package com.tidsec.sarc_bakend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
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
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * Regresion de la Feature 010 (SC-005, FR-013): las cifras, rankings e informes de Consumo, Uptime e Intermitencias y el
 * listado de Inventario son identicos antes y despues de que existan tickets, episodios cerrados y reabiertos, y
 * informes de incidencias emitidos. Lo unico que cambia entre las dos lecturas son las tablas {@code inc_*} y el archivo de
 * informes, que es lo que agrega el modulo nuevo. Los tickets se siembran por SQL sobre los equipos que ya existen en la
 * 004 y la 008, asi que el inventario no cambia.
 */
@SpringBootTest(properties = { "sarc.security.bcrypt-strength=4", "spring.docker.compose.enabled=false" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class Feature010RegressionBaseline {

	private static final String ENERGY_RANGE = "from=2026-10-01&to=2026-11-01";
	private static final String INTERMITTENCY_RANGE = "preset=CUSTOM&from=2026-10-01&to=2026-11-01";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	FileStorage storage;

	/** Lo que leen las pantallas de Consumo, Uptime, Intermitencias e Inventario, ya sin horas de calculo. */
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
			lines.add("## " + role + " intermittency summary");
			lines.add(text(fetch(role, "/api/v1/intermittency/summary?" + INTERMITTENCY_RANGE)));
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

	/** Un ticket por SQL sobre un equipo existente, con su episodio; {@code t4} nulo lo deja abierto. */
	private void ticket(int number, UUID equipment, String t0, String t4) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.update("""
				INSERT INTO inc_ticket (id, ticket_number, client_request_id, state, element_class, equipment_id,
				    equipment_label_at_t0, network_at_t0, infrastructure_type_at_t0, incident_type, report_origin,
				    description, priority, current_responsible_id, current_responsible_name, duplicate_key,
				    last_activity_at, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, 'EQUIPMENT', ?, 'Equipo de prueba', 'KIA', 'CHARGING_POINT', 'DISCONNECTION',
				    'PLATFORM', 'Regresion', 'HIGH', ?, 'Analista', ?, ?, ?, ?, 0)
				""", id, number, UUID.randomUUID(), t4 == null ? "OPEN" : "CLOSED", equipment, UUID.randomUUID(),
				"EQ:" + number, now, now, now);
		UUID episode = UUID.randomUUID();
		UUID version = jdbc.queryForObject("SELECT id FROM inc_reference_version ORDER BY effective_from LIMIT 1", UUID.class);
		jdbc.update("""
				INSERT INTO inc_episode (id, ticket_id, ordinal, state, reference_version_id, closure_result, cause_kind,
				    created_at, updated_at, version)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, 0)
				""", episode, id, t4 == null ? "OPEN" : "CLOSED", version, t4 == null ? null : "RECOVERED",
				t4 == null ? null : "UNDETERMINED", now, now);
		milestone(episode, "T0", "RECORDED", t0, now);
		for (String kind : List.of("T1", "T2", "T3")) {
			milestone(episode, kind, "PENDING", null, now);
		}
		milestone(episode, "T4", t4 == null ? "PENDING" : "RECORDED", t4, now);
	}

	private void milestone(UUID episode, String kind, String status, String at, OffsetDateTime recorded) {
		jdbc.update("""
				INSERT INTO inc_milestone (episode_id, kind, status, occurred_at, retroactive, order_warning_ack, recorded_at)
				VALUES (?, ?, ?, ?, FALSE, FALSE, ?)
				""", episode, kind, status, at == null ? null : OffsetDateTime.parse(at), recorded);
	}

	/** Datos propios del modulo nuevo: tickets abiertos y cerrados en el rango de las demas pantallas, e informes emitidos. */
	private void addIncidentData() throws Exception {
		ticket(9001, Feature008Fixtures.BLOCKED, "2026-10-05T08:00:00-05:00", "2026-10-05T11:00:00-05:00");
		ticket(9002, Feature008Fixtures.FREE, "2026-10-06T10:00:00-05:00", null);
		ticket(9003, Feature008Fixtures.SECOND_BLOCKED, "2026-10-20T09:00:00-05:00", "2026-11-03T09:00:00-05:00");
		for (String role : List.of("GERENTE", "ANALISTA")) {
			fetch(role, "/api/v1/incidents/tickets?size=50");
			fetch(role, "/api/v1/incidents/summary?" + ENERGY_RANGE);
			fetch(role, "/api/v1/incidents/reports/control?format=PDF&" + ENERGY_RANGE);
			fetch(role, "/api/v1/incidents/reports/control?format=XLSX");
			fetch(role, "/api/v1/incidents/reports/management?format=PDF&" + ENERGY_RANGE);
			fetch(role, "/api/v1/incidents/reports/management?format=XLSX&" + ENERGY_RANGE);
		}
	}

	@Test
	void consumptionUptimeIntermittencyAndInventoryAreIdenticalBeforeAndAfterIncidentData() throws Exception {
		jdbc.execute("TRUNCATE inc_evidence, inc_communication, inc_visit, inc_event, inc_milestone_revision, inc_milestone,"
				+ " inc_episode, inc_ticket_relation, inc_ticket_connector, inc_ticket,"
				+ " itm_reviewed_day_event, itm_reviewed_day, itm_window_revision, itm_import_row, itm_window,"
				+ " itm_import_batch, energy_import_row, energy_session_revision, energy_session_review, energy_session,"
				+ " energy_import_batch, asset_equipment, report_issued CASCADE");
		Feature004Fixtures.seed(jdbc, false);
		Feature008Fixtures.seedInventory(jdbc);
		Map<String, byte[]> stored = new HashMap<>();
		when(storage.storeDocument(anyString(), anyString(), any())).thenAnswer(invocation -> {
			String reference = "qa010/report-" + stored.size();
			stored.put(reference, invocation.getArgument(2));
			return reference;
		});
		when(storage.readDocument(anyString())).thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));

		List<String> before = snapshot();
		addIncidentData();
		List<String> after = snapshot();

		assertThat(before).as("el snapshot no puede estar vacio").hasSizeGreaterThan(20);
		assertThat(after).isEqualTo(before);
		// El modulo nuevo archivo sus informes (PDF y Excel de dos roles, de cada tipo) sin tocar los demas tipos.
		assertThat(jdbc.queryForObject("SELECT count(*) FROM report_issued WHERE kind = 'INCIDENT_CONTROL'", Integer.class))
				.isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM report_issued WHERE kind = 'INCIDENT_MANAGEMENT'", Integer.class))
				.isEqualTo(4);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM inc_ticket", Integer.class)).isEqualTo(3);
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
