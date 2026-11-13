package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.support.Feature008Fixtures;

/** Dos capturas simultaneas del mismo punto: el bloqueo por equipo deja pasar una y rechaza la otra. */
class WindowCaptureConcurrencyTest extends IntermittencyApiSupport {

	private static final UUID POINT = Feature008Fixtures.BLOCKED;

	@Test
	void twoThreadsCreatingOverlappingWindowsOfOnePointSaveOnlyOne() throws Exception {
		Cookie session = analyst();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (String[] range : new String[][] { { "10:00", "11:00" }, { "10:30", "11:30" } }) {
				results.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return post(session, "/windows", capture(POINT, window(at(range[0]), at(range[1]))))
							.andReturn().getResponse().getStatus();
				}));
			}
			ready.await();
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}

			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(count("equipment_id = '" + POINT + "' AND state <> 'ANNULLED'")).isEqualTo(1);
	}

	@Test
	void theLoserReceivesTheOverlapCodeNotAServerError() throws Exception {
		Cookie session = analyst();
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int attempt = 0; attempt < 4; attempt++) {
				results.add(pool.submit(() -> {
					go.await();
					var response = post(session, "/windows", capture(POINT, window(at("14:00"), at("15:00"))))
							.andReturn().getResponse();
					return response.getStatus() + ":" + response.getContentAsString().replaceAll(".*\"code\":\"([A-Z_]+)\".*", "$1");
				}));
			}
			go.countDown();
			List<String> outcomes = new ArrayList<>();
			for (Future<String> result : results) {
				outcomes.add(result.get());
			}

			assertThat(outcomes.stream().filter(outcome -> outcome.startsWith("201")).count()).isEqualTo(1);
			assertThat(outcomes.stream().filter(outcome -> outcome.equals("409:WINDOW_DUPLICATE")
					|| outcome.equals("409:WINDOW_OVERLAP")).count()).isEqualTo(3);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(count("equipment_id = '" + POINT + "'")).isEqualTo(1);
	}
}
