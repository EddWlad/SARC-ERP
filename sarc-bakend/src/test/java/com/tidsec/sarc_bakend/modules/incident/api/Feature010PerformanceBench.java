package com.tidsec.sarc_bakend.modules.incident.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.tidsec.sarc_bakend.modules.incident.support.IncidentApiSupport;
import com.tidsec.sarc_bakend.support.Feature010Fixtures;

/**
 * Rendimiento de Incidencias con 5.000 tickets y 50.000 eventos sinteticos (SC-009, quickstart «Rendimiento»). Solo con
 * {@code -Dsarc.qa.performance010=true}: el listado con tres filtros, el resumen de un año, el Control de todo el historial
 * y la Gestion de un año en PDF y Excel, y la creacion. Cada medida se imprime y se compara con su limite.
 */
@EnabledIfSystemProperty(named = "sarc.qa.performance010", matches = "true")
class Feature010PerformanceBench extends IncidentApiSupport {

	private static final int TICKETS = 5_000;
	private static final int EVENTS_PER_TICKET = 10;

	private final List<String> results = new ArrayList<>();

	private void seed() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		UUID version = jdbc.queryForObject("SELECT id FROM inc_reference_version ORDER BY effective_from LIMIT 1", UUID.class);
		List<Object[]> tickets = new ArrayList<>();
		List<Object[]> episodes = new ArrayList<>();
		List<Object[]> milestones = new ArrayList<>();
		List<Object[]> events = new ArrayList<>();
		UUID responsible = userId(com.tidsec.sarc_bakend.modules.identity.domain.model.Role.ANALISTA);
		for (int number = 1; number <= TICKETS; number++) {
			UUID ticket = UUID.randomUUID();
			UUID episode = UUID.randomUUID();
			boolean closed = number % 3 != 0;
			OffsetDateTime t0 = now.minusDays(number % 360).minusHours(number % 20 + 1);
			OffsetDateTime t4 = closed ? t0.plusMinutes(90 + number % 120) : null;
			UUID equipment = number % 2 == 0 ? Feature010Fixtures.KIA_STATION : Feature010Fixtures.JAC_POINT;
			tickets.add(new Object[] { ticket, number, UUID.randomUUID(), closed ? "CLOSED" : "OPEN", equipment,
					number % 2 == 0 ? "KIA" : "JAC", number % 5 == 0 ? "CRITICAL" : "HIGH", responsible, "EQ:" + number, now, now,
					now });
			episodes.add(new Object[] { episode, ticket, closed ? "CLOSED" : "OPEN", version,
					closed ? "RECOVERED" : null, closed ? "UNDETERMINED" : null, now, now });
			milestones.add(new Object[] { episode, "T0", "RECORDED", t0, now });
			for (String kind : List.of("T1", "T2", "T3")) {
				milestones.add(new Object[] { episode, kind, "RECORDED", t0.plusMinutes(5L * kind.charAt(1) - '0'), now });
			}
			milestones.add(new Object[] { episode, "T4", closed ? "RECORDED" : "PENDING", t4, now });
			for (int index = 0; index < EVENTS_PER_TICKET; index++) {
				events.add(new Object[] { UUID.randomUUID(), ticket, episode, t0.plusMinutes(index), now });
			}
		}
		jdbc.batchUpdate("""
				INSERT INTO inc_ticket (id, ticket_number, client_request_id, state, element_class, equipment_id,
				    equipment_label_at_t0, network_at_t0, infrastructure_type_at_t0, incident_type, report_origin,
				    description, priority, current_responsible_id, current_responsible_name, duplicate_key,
				    last_activity_at, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, 'EQUIPMENT', ?, 'Equipo de prueba', ?, 'CHARGING_POINT', 'DISCONNECTION', 'PLATFORM',
				    'Sintetico', ?, ?, 'Analista', ?, ?, ?, ?, 0)
				""", tickets);
		jdbc.batchUpdate("""
				INSERT INTO inc_episode (id, ticket_id, ordinal, state, reference_version_id, closure_result, cause_kind,
				    created_at, updated_at, version)
				VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, 0)
				""", episodes);
		jdbc.batchUpdate("""
				INSERT INTO inc_milestone (episode_id, kind, status, occurred_at, retroactive, order_warning_ack, recorded_at)
				VALUES (?, ?, ?, ?, FALSE, FALSE, ?)
				""", milestones);
		jdbc.batchUpdate("""
				INSERT INTO inc_event (id, ticket_id, episode_id, type, occurred_at, recorded_at, retroactive, text, notifiable)
				VALUES (?, ?, ?, 'OBSERVATION', ?, ?, FALSE, 'Evento sintetico', FALSE)
				""", events);
	}

	/** La primera llamada de cada operacion calienta la JVM y Hibernate y no se mide: el limite es del servicio en marcha. */
	private <T> T measure(String label, long limitMillis, Callable<T> action) throws Exception {
		if (!label.startsWith("Creación")) {
			action.call();
		}
		long start = System.nanoTime();
		T value = action.call();
		long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
		results.add("%s: %d ms (limite %d ms)".formatted(label, millis, limitMillis));
		System.out.println("[PERF010] " + results.getLast());
		assertThat(millis).as(label).isLessThanOrEqualTo(limitMillis);
		return value;
	}

	@Test
	void theKeyOperationsStayWithinTheirLimitsWith5000TicketsAnd50000Events() throws Exception {
		seed();
		assertThat(count("inc_ticket")).isEqualTo(TICKETS);
		assertThat(count("inc_event")).isEqualTo(TICKETS * EVENTS_PER_TICKET);
		LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Guayaquil"));
		String year = "from=" + today.minusDays(364) + "&to=" + today;

		measure("Listado con 3 filtros", 1_000, () -> get(analyst(), "/tickets?state=OPEN&priority=HIGH&network=KIA&size=20")
				.andExpect(status().isOk()).andReturn());
		measure("Resumen de un año", 2_000, () -> get(analyst(), "/summary?" + year).andExpect(status().isOk()).andReturn());
		measure("Control de todo el historial en PDF", 10_000, () -> get(analyst(), "/reports/control?format=PDF")
				.andExpect(status().isOk()).andReturn());
		measure("Control de todo el historial en Excel", 10_000, () -> get(analyst(), "/reports/control?format=XLSX")
				.andExpect(status().isOk()).andReturn());
		measure("Gestión de un año en PDF", 10_000, () -> get(analyst(), "/reports/management?format=PDF&" + year)
				.andExpect(status().isOk()).andReturn());
		measure("Gestión de un año en Excel", 10_000, () -> get(analyst(), "/reports/management?format=XLSX&" + year)
				.andExpect(status().isOk()).andReturn());
		measure("Creación de un ticket", 500, () -> createTicket(analyst(),
				equipmentBody(Feature010Fixtures.KIA_STATION, "WAIT_FOR_CONNECTION", "Prueba de carga"))
				.andExpect(status().isCreated()).andReturn());

		System.out.println("[PERF010] " + Instant.now() + " resumen: " + String.join(" | ", results));
	}
}
