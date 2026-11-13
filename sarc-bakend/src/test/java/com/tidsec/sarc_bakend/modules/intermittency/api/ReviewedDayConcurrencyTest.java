package com.tidsec.sarc_bakend.modules.intermittency.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.Cookie;

/** Dos editores marcan el mismo dia a la vez: el bloqueo por dia deja un solo evento y ningun error de servidor. */
class ReviewedDayConcurrencyTest extends IntermittencyApiSupport {

	private static final LocalDate YESTERDAY = LocalDate.now(ZoneId.of("America/Guayaquil")).minusDays(1);

	@BeforeEach
	void notNearMidnight() {
		LocalTime now = LocalTime.now(ZoneId.of("America/Guayaquil"));
		Assumptions.assumeTrue(now.isAfter(LocalTime.of(0, 10)) && now.isBefore(LocalTime.of(23, 50)),
				"Demasiado cerca de la medianoche de Guayaquil");
	}

	@Test
	void fourThreadsMarkingTheSameDayLeaveASingleMarkAndASingleEvent() throws Exception {
		Cookie session = analyst();
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int attempt = 0; attempt < 4; attempt++) {
				results.add(pool.submit(() -> {
					go.await();
					return put(session, "/reviewed-days/" + YESTERDAY).andReturn().getResponse().getStatus();
				}));
			}
			go.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day WHERE active", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM itm_reviewed_day_event WHERE action = 'MARKED'",
				Integer.class)).isEqualTo(1);
	}
}
