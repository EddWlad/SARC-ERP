package com.tidsec.sarc_bakend.modules.uptime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class UptimeConcurrencyTest extends UptimeApiSupport {

	/** Lanza las dos peticiones a la vez y devuelve sus estados. */
	private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
		CountDownLatch gate = new CountDownLatch(2);
		try (var pool = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (Callable<Integer> call : List.of(first, second)) {
				results.add(pool.submit(() -> {
					gate.countDown();
					gate.await();
					return call.call();
				}));
			}
			return List.of(results.get(0).get(), results.get(1).get());
		}
	}

	@Test
	void twoCorrectionsOnlyOneWins() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		long version = version(id);
		Callable<Integer> correct = () -> send(analyst, "/measurements/" + id + "/corrections",
				correction(version, "1440", "0")).andReturn().getResponse().getStatus();

		assertThat(race(correct, correct)).containsExactlyInAnyOrder(200, 409);
	}

	@Test
	void twoValidationsOnlyOneWins() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		long version = version(id);
		Callable<Integer> validate = () -> send(analyst, "/measurements/" + id + "/validate",
				"{\"version\":%d}".formatted(version)).andReturn().getResponse().getStatus();

		assertThat(race(validate, validate)).containsExactlyInAnyOrder(200, 409);
	}

	@Test
	void concurrentIdenticalRegistrationKeepsOneMeasurement() throws Exception {
		Callable<Integer> register = () -> send(analyst, "/measurements", measurement(JAC_1, "1401", "39")).andReturn()
				.getResponse().getStatus();

		assertThat(race(register, register)).containsExactlyInAnyOrder(200, 201);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM upt_measurement", Integer.class)).isEqualTo(1);
	}

	@Test
	void batchSkipsObservedStaleAndMissing() throws Exception {
		String ready = registered(JAC_1, "1401", "39").get("id").toString();
		String observed = registered(JAC_2, "1000", "0").get("id").toString();
		UUID missing = UUID.randomUUID();
		String body = """
				{"items":[{"id":"%s","version":%d},{"id":"%s","version":%d},{"id":"%s","version":0}]}
				""".formatted(ready, version(ready), observed, version(observed), missing);

		send(analyst, "/measurements/validation", body).andExpect(status().isOk())
				.andExpect(jsonPath("$.validated").value(1))
				.andExpect(jsonPath("$.skipped[?(@.id=='%s')].reason".formatted(observed)).value("OBSERVED"))
				.andExpect(jsonPath("$.skipped[?(@.id=='%s')].reason".formatted(missing)).value("NOT_FOUND"));

		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":0}]}
				""".formatted(observed)).andExpect(jsonPath("$.validated").value(0));
	}

	@Test
	void batchReportsStaleAndNotPending() throws Exception {
		String id = registered(JAC_1, "1401", "39").get("id").toString();
		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":99}]}
				""".formatted(id)).andExpect(jsonPath("$.skipped[0].reason").value("STALE_VERSION"));
		validate(id);
		send(analyst, "/measurements/validation", """
				{"items":[{"id":"%s","version":%d}]}
				""".formatted(id, version(id))).andExpect(jsonPath("$.skipped[0].reason").value("NOT_PENDING"));
	}

	@Test
	void batchOverFiveHundredIsRejected() throws Exception {
		StringBuilder items = new StringBuilder();
		for (int i = 0; i < 501; i++) {
			items.append(i == 0 ? "" : ",").append("{\"id\":\"").append(UUID.randomUUID()).append("\",\"version\":0}");
		}
		send(analyst, "/measurements/validation", "{\"items\":[" + items + "]}").andExpect(status().is4xxClientError());
	}
}
