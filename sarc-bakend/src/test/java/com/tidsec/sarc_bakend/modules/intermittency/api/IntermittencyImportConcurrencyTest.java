package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.tidsec.sarc_bakend.support.Feature008Fixtures;
import com.tidsec.sarc_bakend.support.WebSessions;

/** Escrituras simultaneas sobre la misma fila o el mismo punto: gana una y nunca quedan ventanas solapadas. */
class IntermittencyImportConcurrencyTest extends IntermittencyImportSupport {

	private static final String CODE = Feature008Fixtures.BLOCKED_CODE;

	@Test
	void twoEditsOfTheSameRowWithTheSameVersionLeaveOneWinnerAndAConflict() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "", "08:00", "08:30")), "version.csv");
		String url = BASE + BATCHES + "/" + id + "/rows/" + rowId(id, 2);
		long version = rowVersion(id, 2);
		Cookie session = analyst();
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (String comment : new String[] { "Primero", "Segundo" }) {
				results.add(pool.submit(() -> {
					go.await();
					return mvc.perform(MockMvcRequestBuilders.patch(url).cookie(session).with(WebSessions.xsrf())
							.contentType(MediaType.APPLICATION_JSON).content(
									"{\"version\":%d,\"edits\":[{\"target\":\"comment\",\"value\":\"%s\"}]}"
											.formatted(version, comment)))
							.andReturn().getResponse().getStatus();
				}));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}

			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat((List<?>) JsonPath.read(row(id, 2), "$.edits")).hasSize(1);
	}

	@Test
	void confirmingABatchWhileAManualCaptureWritesTheSamePointNeverLeavesOverlaps() throws Exception {
		String id = uploadReady(csv(entry(2, "2026-10-05", CODE, "0:30", "10:00", "10:30")), "carrera.csv");
		Cookie session = analyst();
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> confirming = pool.submit(() -> {
				go.await();
				return confirm(id).andReturn().getResponse().getStatus();
			});
			Future<Integer> capturing = pool.submit(() -> {
				go.await();
				return post(session, "/windows", capture(Feature008Fixtures.BLOCKED,
						window(at("2026-10-05", "10:15"), at("2026-10-05", "10:45")))).andReturn().getResponse()
						.getStatus();
			});
			go.countDown();
			confirming.get();
			capturing.get();
		}
		finally {
			pool.shutdownNow();
		}
		// Espera a que el trabajo en segundo plano, si empezo, termine.
		for (int attempt = 0; attempt < 100; attempt++) {
			String status = JsonPath.read(batch(id), "$.status");
			if (!status.equals("CONFIRMING")) {
				break;
			}
			Thread.sleep(100);
		}

		assertThat(count("equipment_id = '" + Feature008Fixtures.BLOCKED + "' AND state <> 'ANNULLED'")).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM itm_window a JOIN itm_window b ON a.equipment_id = b.equipment_id AND a.id < b.id
				WHERE a.state <> 'ANNULLED' AND b.state <> 'ANNULLED'
				  AND tstzrange(a.start_at, coalesce(a.end_at, 'infinity'), '[)')
				      && tstzrange(b.start_at, coalesce(b.end_at, 'infinity'), '[)')
				""", Integer.class)).isZero();
	}
}
