package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.tidsec.sarc_bakend.modules.intermittency.support.SyntheticOperationalLog.Entry;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/**
 * QA opt-in (T123): mide con datos sinteticos sobre base desechable y escribe
 * {@code target/intermittency-performance008.txt}. Verifica los criterios medibles de la feature: la vista previa
 * de 248 filas en menos de 30 s (SC-002), un resumen quincenal con 6.000 ventanas en menos de 2 s y una captura en
 * menos de 1 s (plan, Performance Goals). Se ejecuta con {@code -Dsarc.qa.performance008=true}.
 */
@EnabledIfSystemProperty(named = "sarc.qa.performance008", matches = "true")
class Feature008PerformanceBench extends IntermittencyImportSupport {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final List<UUID> POINTS = List.of(Feature008Fixtures.BLOCKED, Feature008Fixtures.SECOND_BLOCKED,
			Feature008Fixtures.FREE, Feature008Fixtures.FREE_WITHOUT_CODE, Feature008Fixtures.TEST_POINT);
	private static final int WINDOWS_PER_POINT = 1_200;
	private static final int ROUNDS = 15;

	private final List<String> report = new ArrayList<>();

	private static long median(List<Long> values) {
		List<Long> sorted = values.stream().sorted().toList();
		return sorted.get(sorted.size() / 2);
	}

	private long measure(Supplier<Long> action) {
		List<Long> millis = new ArrayList<>();
		for (int round = 0; round < ROUNDS; round++) {
			millis.add(action.get());
		}
		return median(millis);
	}

	private long timed(ThrowingRunnable action) {
		long started = System.nanoTime();
		try {
			action.run();
		}
		catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
		return (System.nanoTime() - started) / 1_000_000;
	}

	private interface ThrowingRunnable {

		void run() throws Exception;
	}

	/** 248 filas como el registro operativo: dos ventanas por fila sobre cinco puntos y 18 dias. */
	private byte[] operationalLog() {
		String[] codes = { Feature008Fixtures.BLOCKED_CODE, Feature008Fixtures.SECOND_BLOCKED_CODE,
				Feature008Fixtures.FREE_CODE, Feature008Fixtures.TEST_CODE };
		List<Entry> rows = new ArrayList<>();
		for (int number = 2; number < 250; number++) {
			String day = LocalDate.of(2026, 10, 1).plusDays((number - 2) % 18).toString();
			rows.add(entry(number, day, codes[number % codes.length], "0:50", "08:00", "08:20", "14:00", "14:30"));
		}
		return csv(rows.toArray(new Entry[0]));
	}

	/** 6.000 ventanas confirmadas de 30 minutos, cuatro por dia y punto, sin solaparse. */
	private void seedWindows() {
		List<Object[]> rows = new ArrayList<>();
		LocalDate first = LocalDate.of(2026, 1, 1);
		for (UUID point : POINTS) {
			for (int index = 0; index < WINDOWS_PER_POINT; index++) {
				Instant start = first.plusDays(index / 4).atTime(6 * (index % 4), 0).atZone(ZONE).toInstant();
				rows.add(new Object[] { UUID.randomUUID(), point, Timestamp.from(start),
						Timestamp.from(start.plusSeconds(1_800)) });
			}
		}
		jdbc.batchUpdate("""
				INSERT INTO itm_window (id, equipment_id, point_label, start_at, end_at, duration_minutes, state,
				    review_reasons, source, current_revision, created_at, updated_at, version)
				VALUES (?, ?, 'QA008 Punto', ?, ?, 30, 'CONFIRMED', '[]'::jsonb, 'MANUAL', 1, now(), now(), 0)
				""", rows);
	}

	@Test
	void measureAgainstTheMeasurableCriteria() throws Exception {
		// SC-002: la vista previa de 248 filas.
		byte[] log = operationalLog();
		long preview = timed(() -> uploadReady(log, "registro-248.csv"));
		report.add("Vista previa de 248 filas: %d ms".formatted(preview));
		assertThat(preview).as("SC-002: vista previa de 248 filas").isLessThan(30_000);

		seedWindows();
		long windows = jdbc.queryForObject("SELECT count(*) FROM itm_window WHERE state = 'CONFIRMED'", Long.class);
		report.add("Ventanas sembradas: " + windows);
		assertThat(windows).isEqualTo(6_000);

		String range = "preset=CUSTOM&from=2026-10-01&to=2026-10-16";
		long summary = measure(() -> timed(() -> get(analyst(), "/summary?" + range).andExpect(status().isOk())));
		long year = measure(() -> timed(() -> get(analyst(), "/summary?preset=CUSTOM&from=2026-01-01&to=2026-12-31")
				.andExpect(status().isOk())));
		report.add("Resumen quincenal con 6.000 ventanas (mediana de %d): %d ms".formatted(ROUNDS, summary));
		report.add("Resumen de un anio con 6.000 ventanas (mediana): %d ms".formatted(year));
		assertThat(summary).as("resumen quincenal").isLessThan(2_000);
		assertThat(year).as("resumen anual").isLessThan(2_000);

		String availability = "/availability?preset=CUSTOM&from=2026-01-01&to=2026-11-01";
		report.add("Disponibilidad de 10 meses (mediana): %d ms"
				.formatted(measure(() -> timed(() -> get(analyst(), availability).andExpect(status().isOk())))));
		report.add("Informe PDF de un anio con 6.000 ventanas: %d ms".formatted(timed(
				() -> get(analyst(), "/report?format=PDF&preset=CUSTOM&from=2026-01-01&to=2026-12-31")
						.andExpect(status().isOk()))));
		report.add("Informe Excel de un anio con 6.000 ventanas: %d ms".formatted(timed(
				() -> get(analyst(), "/report?format=XLSX&preset=CUSTOM&from=2026-01-01&to=2026-12-31")
						.andExpect(status().isOk()))));

		long capture = measure(() -> {
			LocalDate day = LocalDate.of(2026, 10, 29).plusDays(captureDay++);
			return timed(() -> post(analyst(), "/windows",
					capture(Feature008Fixtures.BLOCKED, window(at(day.toString(), "01:00"), at(day.toString(), "01:20")),
							window(at(day.toString(), "02:00"), at(day.toString(), "02:20")),
							window(at(day.toString(), "03:00"), at(day.toString(), "03:20")),
							window(at(day.toString(), "04:00"), at(day.toString(), "04:20")),
							window(at(day.toString(), "05:00"), at(day.toString(), "05:20"))))
					.andExpect(status().isCreated()));
		});
		report.add("Captura de 5 ventanas (mediana): %d ms".formatted(capture));
		assertThat(capture).as("captura").isLessThan(1_000);

		Files.write(Path.of("target/intermittency-performance008.txt"), report);
	}

	private int captureDay;
}
