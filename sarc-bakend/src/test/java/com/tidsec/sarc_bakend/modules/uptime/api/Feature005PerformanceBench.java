package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.support.Feature005Fixtures;

/**
 * QA opt-in (T123): mide con datos sinteticos de R-18 sobre base desechable y escribe
 * {@code target/uptime-performance.txt}. Son mediciones, no limites funcionales.
 */
@EnabledIfSystemProperty(named = "sarc.qa.performance", matches = "true")
class Feature005PerformanceBench extends UptimeImportSupport {

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
	private static final int WEEKS = 104;
	private static final int ROWS = 2000;
	private static final int ROUNDS = 20;

	private final List<String> report = new ArrayList<>();

	@Test
	void measure() throws Exception {
		for (int connectors : List.of(300, 1000)) {
			long started = System.nanoTime();
			seed(connectors);
			report.add("## %d conectores, %d mediciones (siembra %d s)".formatted(connectors,
					jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Long.class),
					(System.nanoTime() - started) / 1_000_000_000));
			probe("Dashboard resumen, apertura", "/summary", ROUNDS);
			probe("Dashboard resumen, red", "/summary?network=JAC", ROUNDS);
			probe("Ranking por conector", "/results/connectors?page=0&size=50", ROUNDS);
			probe("Resumen por equipo", "/results/equipment?page=0&size=50", ROUNDS);
			probe("Grilla de captura", "/grid?periodStart=2026-10-30T00:00:00-05:00&periodEnd=2026-10-31T00:00:00-05:00",
					ROUNDS);
			probe("Reporte PDF", "/report?format=PDF", 5);
			probe("Reporte Excel", "/report?format=XLSX", 5);
		}
		importRows();
		Files.write(Path.of("target/uptime-performance.txt"), report);
	}

	private void seed(int connectors) {
		int existing = jdbc.queryForObject("SELECT count(*) FROM asset_connector WHERE ordinal = 1 AND equipment_id IN "
				+ "(SELECT id FROM asset_equipment WHERE name LIKE 'QA005 Perf %')", Integer.class);
		jdbc.update("""
				INSERT INTO asset_equipment (id, network, infrastructure_type, brand, name, serial, assigned_code,
				    admin_status, schedule_mode, created_at, updated_at, version)
				SELECT gen_random_uuid(), 'JAC', 'CHARGING_STATION', 'BENNY', 'QA005 Perf ' || g, 'perf-' || g,
				    'perf-' || g, 1, 'NOT_DEFINED', now(), now(), 0
				FROM generate_series(?, ?) g
				""", existing + 1, connectors);
		jdbc.update("""
				INSERT INTO asset_connector (id, equipment_id, ordinal, connector_type_id, active, created_at,
				    updated_at, version)
				SELECT gen_random_uuid(), e.id, 1, ?, TRUE, now(), now(), 0
				FROM asset_equipment e
				WHERE e.name LIKE 'QA005 Perf %'
				  AND NOT EXISTS (SELECT 1 FROM asset_connector c WHERE c.equipment_id = e.id)
				""", Feature005Fixtures.CCS2);
		jdbc.execute("DROP TABLE IF EXISTS qa_perf");
		jdbc.execute("""
				CREATE TABLE qa_perf AS
				SELECT gen_random_uuid() AS id, c.id AS connector_id, c.equipment_id, w.kind, w.period_start, w.period_end
				FROM asset_connector c
				JOIN asset_equipment e ON e.id = c.equipment_id AND e.name LIKE 'QA005 Perf %%'
				CROSS JOIN LATERAL (
				    SELECT k.kind, s.week_end - k.span - k.shift AS period_start, s.week_end - k.shift AS period_end
				    FROM generate_series(0, %d) n
				    CROSS JOIN LATERAL (SELECT timestamptz '2026-10-26 00:00:00-05' - n * interval '7 days' AS week_end) s
				    CROSS JOIN (VALUES
				        ('EXACT_WEEK', interval '7 days', interval '0'),
				        ('ROLLING_24H', interval '1 day', interval '3 hours'),
				        ('ROLLING_7D', interval '7 days', interval '4 hours'),
				        ('ROLLING_30D', interval '30 days', interval '5 hours')) k(kind, span, shift)
				) w
				WHERE NOT EXISTS (SELECT 1 FROM upt_measurement m WHERE m.connector_id = c.id)
				""".formatted(WEEKS - 1));
		jdbc.update("""
				INSERT INTO upt_measurement (id, connector_id, equipment_id, source, period_start, period_end, period_zone,
				    latest_revision, effective_revision, created_at, updated_at, version)
				SELECT id, connector_id, equipment_id, 'ENERLINK', period_start, period_end, 'America/Guayaquil', 1, 1,
				    now(), now(), 0
				FROM qa_perf
				""");
		jdbc.update("""
				INSERT INTO upt_measurement_revision (id, measurement_id, revision, reading_class, period_type,
				    available_minutes, occupied_minutes, fault_minutes, offline_minutes, review_state, observations,
				    origin, equipment_label, connector_type_label, exact_confirmed_at, entered_at, validated_at)
				SELECT gen_random_uuid(), p.id, 1, 'MEASURED', p.kind,
				    (extract(epoch FROM p.period_end - p.period_start) / 60)::int - 100, 60, 10, 30, 'VALIDATED', '[]',
				    'MANUAL', 'Equipo de rendimiento', 'CCS2', now(), now(), now()
				FROM qa_perf p
				""");
		jdbc.execute("DROP TABLE qa_perf");
		jdbc.execute("ANALYZE upt_measurement");
		jdbc.execute("ANALYZE upt_measurement_revision");
	}

	private void probe(String label, String path, int rounds) throws Exception {
		long[] millis = new long[rounds];
		for (int round = 0; round < rounds; round++) {
			long started = System.nanoTime();
			mvc.perform(get("/api/v1/uptime" + path).cookie(analyst)).andExpect(status().isOk());
			millis[round] = (System.nanoTime() - started) / 1_000_000;
		}
		Arrays.sort(millis);
		report.add("%s: p50 %d ms, p95 %d ms, max %d ms (%d rondas)".formatted(label, millis[rounds / 2],
				millis[(int) Math.ceil(rounds * 0.95) - 1], millis[rounds - 1], rounds));
	}

	private void importRows() throws Exception {
		LocalDateTime first = LocalDateTime.of(2020, 1, 1, 0, 0);
		String[] rows = IntStream.range(0, ROWS).mapToObj(day -> row(SITE, CHARGER, "1",
				STAMP.format(first.plusDays(day)), STAMP.format(first.plusDays(day + 1L)), "1440", "0"))
				.toArray(String[]::new);
		long started = System.nanoTime();
		String body = send(analyst, "/imports/paste", paste("ROLLING_24H", rows)).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");
		awaitLong(id, "READY");
		report.add("## Importación de %d filas".formatted(ROWS));
		report.add("Vista previa: %d ms".formatted((System.nanoTime() - started) / 1_000_000));
		started = System.nanoTime();
		send(analyst, "/imports/" + id + "/confirm", "{\"version\":%d,\"exactConfirmed\":false}".formatted(batchVersion(id)))
				.andExpect(status().isAccepted());
		awaitLong(id, "COMPLETED");
		report.add("Confirmación: %d ms, %d mediciones creadas".formatted((System.nanoTime() - started) / 1_000_000,
				jdbc.queryForObject("SELECT count(*) FROM upt_import_row WHERE batch_id = ?::uuid AND outcome = 'APPLIED'",
						Long.class, java.util.UUID.fromString(id))));
	}

	private void awaitLong(String id, String expected) throws InterruptedException {
		long deadline = System.nanoTime() + 300_000_000_000L;
		while (System.nanoTime() < deadline) {
			String current = jdbc.queryForObject("SELECT status FROM upt_import_batch WHERE id = ?::uuid", String.class, id);
			if (expected.equals(current)) return;
			Thread.sleep(100);
		}
		throw new AssertionError("Lote sin llegar a " + expected);
	}
}
