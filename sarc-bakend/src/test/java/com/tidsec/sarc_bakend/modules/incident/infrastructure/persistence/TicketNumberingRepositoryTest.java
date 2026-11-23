package com.tidsec.sarc_bakend.modules.incident.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tidsec.sarc_bakend.TestcontainersConfiguration;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketNumbering;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketNumberingRepository;
import com.tidsec.sarc_bakend.shared.storage.FileStorage;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/** Contador de una sola fila: consecutivo, sin huecos, y bloqueado una vez creado el primer ticket (research R2). */
@SpringBootTest(properties = "sarc.security.bcrypt-strength=4")
@Import(TestcontainersConfiguration.class)
class TicketNumberingRepositoryTest {

	@MockitoBean
	FileStorage fileStorage;

	@Autowired
	TicketNumberingRepository numbering;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PlatformTransactionManager transactions;

	private TransactionTemplate tx;

	@BeforeEach
	void reset() {
		Feature010Fixtures.clearIncidents(jdbc);
		Feature010Fixtures.clearNumbering(jdbc);
		tx = new TransactionTemplate(transactions);
	}

	@AfterEach
	void restore() {
		Feature010Fixtures.clearIncidents(jdbc);
		Feature010Fixtures.clearNumbering(jdbc);
	}

	@Test
	void anUnconfiguredCounterAssignsNothing() {
		assertThat(numbering.current().configured()).isFalse();
		assertThat(take()).isEmpty();
		assertThat(numbering.ticketsExist()).isFalse();
	}

	@Test
	void numbersAreConsecutiveFromTheStartNumber() {
		assertThat(configure(9001)).isTrue();
		List<Integer> assigned = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			assigned.add(take().orElseThrow());
		}
		assertThat(assigned).containsExactly(9001, 9002, 9003, 9004, 9005);
		assertThat(numbering.current()).isEqualTo(new TicketNumbering(9001, 9006, "TEST"));
		assertThat(numbering.current().nextCode().orElseThrow().text()).isEqualTo("TICKET-9006");
	}

	@Test
	void aNumberTakenInATransactionThatFailsIsNotLost() {
		configure(100);
		tx.execute(status -> {
			assertThat(numbering.nextNumber()).contains(100);
			status.setRollbackOnly();
			return null;
		});
		assertThat(take()).contains(100);
		assertThat(take()).contains(101);
	}

	@Test
	void concurrentTakersNeverReceiveTheSameNumber() throws Exception {
		configure(1);
		int takers = 40;
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Integer>> jobs = new ArrayList<>();
			for (int i = 0; i < takers; i++) {
				jobs.add(() -> new TransactionTemplate(transactions).execute(status -> numbering.nextNumber())
						.orElseThrow());
			}
			List<Integer> numbers = new ArrayList<>();
			for (Future<Integer> future : pool.invokeAll(jobs)) {
				numbers.add(future.get(30, TimeUnit.SECONDS));
			}
			assertThat(numbers).doesNotHaveDuplicates().hasSize(takers)
					.containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.rangeClosed(1, takers).boxed().toList());
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void theStartNumberCannotChangeOnceATicketExists() {
		assertThat(configure(9001)).isTrue();
		insertTicket(9001);
		assertThat(numbering.ticketsExist()).isTrue();

		Boolean reconfigured = tx.execute(status -> numbering.configure(5000, "TEST", UUID.randomUUID(), Instant.now()));
		assertThat(reconfigured).isFalse();
		assertThat(numbering.current().startNumber()).isEqualTo(9001);
	}

	@Test
	void theCounterCanBeConfiguredAgainWhileNoTicketExists() {
		assertThat(configure(9001)).isTrue();
		assertThat(configure(9500)).isTrue();
		assertThat(numbering.current()).isEqualTo(new TicketNumbering(9500, 9500, "TEST"));
	}

	@Test
	void configuringWaitsForACreationInProgressAndThenSeesItsTicket() throws Exception {
		configure(9001);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			java.util.concurrent.CountDownLatch numberTaken = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.CountDownLatch mayCommit = new java.util.concurrent.CountDownLatch(1);
			Future<Optional<Integer>> creation = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
				Optional<Integer> number = numbering.nextNumber();
				insertTicket(number.orElseThrow());
				numberTaken.countDown();
				await(mayCommit);
				return number;
			}));
			assertThat(numberTaken.await(30, TimeUnit.SECONDS)).isTrue();

			Future<Boolean> configuration = Executors.newSingleThreadExecutor().submit(
					() -> new TransactionTemplate(transactions)
							.execute(status -> numbering.configure(1, "TEST", UUID.randomUUID(), Instant.now())));
			Thread.sleep(500);
			assertThat(configuration.isDone()).as("configurar espera la creacion en curso").isFalse();
			mayCommit.countDown();

			assertThat(creation.get(30, TimeUnit.SECONDS)).contains(9001);
			assertThat(configuration.get(30, TimeUnit.SECONDS)).as("ya hay un ticket: no se puede reconfigurar").isFalse();
			assertThat(numbering.current().startNumber()).isEqualTo(9001);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Optional<Integer> take() {
		return tx.execute(status -> numbering.nextNumber());
	}

	private boolean configure(int start) {
		return Boolean.TRUE.equals(tx.execute(status -> numbering.configure(start, "TEST", UUID.randomUUID(), Instant.now())));
	}

	private static void await(java.util.concurrent.CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void insertTicket(int number) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbc.update("""
				INSERT INTO inc_ticket (id, ticket_number, client_request_id, state, element_class, incident_type,
				    report_origin, description, priority, current_responsible_id, current_responsible_name,
				    duplicate_key, last_activity_at, created_at, updated_at, version)
				VALUES (?, ?, ?, 'OPEN', 'CHATBOT_SERVICE', 'CHATBOT_SERVICE_FAILURE', 'CHATBOT', 'Prueba', 'MEDIUM',
				    ?, 'Analista', 'CHATBOT', ?, ?, ?, 0)
				""", UUID.randomUUID(), number, UUID.randomUUID(), UUID.randomUUID(), now, now, now);
	}
}
