package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/** SC-003, FR-015: la creacion simultanea no produce numeros duplicados, reutilizados ni huecos. */
class IncidentNumberingConcurrencyTest extends IncidentApiSupport {

	private static final int CREATIONS = 50;

	@Test
	void fiftyParallelCreationsGetFiftyConsecutiveNumbers() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(16);
		try {
			CountDownLatch gate = new CountDownLatch(1);
			List<Callable<Integer>> jobs = new ArrayList<>();
			for (int i = 0; i < CREATIONS; i++) {
				// Todas sobre el mismo equipo, con motivo para saltar el aviso de duplicado: lo que se prueba es el numero.
				String body = equipmentBody(UUID.randomUUID(), Feature010Fixtures.KIA_STATION, List.of(), "DISCONNECTION",
						"Creacion " + i, ",\"duplicateOverrideReason\":\"Prueba de concurrencia\"");
				jobs.add(() -> {
					gate.await();
					return createTicket(analyst(), body).andReturn().getResponse().getStatus();
				});
			}
			List<Future<Integer>> futures = new ArrayList<>();
			jobs.forEach(job -> futures.add(pool.submit(job)));
			gate.countDown();
			for (Future<Integer> future : futures) {
				assertThat(future.get(120, TimeUnit.SECONDS)).isEqualTo(201);
			}
		}
		finally {
			pool.shutdownNow();
		}

		List<Integer> numbers = jdbc.queryForList("SELECT ticket_number FROM inc_ticket ORDER BY ticket_number",
				Integer.class);
		assertThat(numbers).hasSize(CREATIONS).doesNotHaveDuplicates().containsExactlyElementsOf(
				IntStream.range(Feature010Fixtures.TEST_START_NUMBER, Feature010Fixtures.TEST_START_NUMBER + CREATIONS)
						.boxed().toList());
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class))
				.isEqualTo(Feature010Fixtures.TEST_START_NUMBER + CREATIONS);
	}

	@Test
	void aRaceBetweenCreatingAndConfiguringNeverLeavesAnInconsistentSequence() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch gate = new CountDownLatch(1);
			Future<Integer> creation = pool.submit(() -> {
				gate.await();
				return createTicket(analyst(), equipmentBody(Feature010Fixtures.KIA_STATION, "DISCONNECTION", "x"))
						.andReturn().getResponse().getStatus();
			});
			Future<Integer> configuration = pool.submit(() -> {
				gate.await();
				return put(admin(), "/numbering",
						"{\"startNumber\":9500,\"environment\":\"TEST\",\"confirmation\":\"TICKET-9500\"}").andReturn()
						.getResponse().getStatus();
			});
			gate.countDown();
			assertThat(creation.get(60, TimeUnit.SECONDS)).isEqualTo(201);
			// Reconfigurar antes del primer ticket es valido (200); despues, el numero inicial esta bloqueado (409).
			assertThat(configuration.get(60, TimeUnit.SECONDS)).isIn(200, 409);
		}
		finally {
			pool.shutdownNow();
		}

		Integer start = jdbc.queryForObject("SELECT start_number FROM inc_ticket_numbering", Integer.class);
		assertThat(jdbc.queryForList("SELECT ticket_number FROM inc_ticket", Integer.class))
				.as("el primer ticket lleva el numero inicial vigente, nunca uno de la secuencia descartada")
				.containsExactly(start);
		assertThat(jdbc.queryForObject("SELECT next_number FROM inc_ticket_numbering", Integer.class))
				.isEqualTo(start + 1);
	}
}
