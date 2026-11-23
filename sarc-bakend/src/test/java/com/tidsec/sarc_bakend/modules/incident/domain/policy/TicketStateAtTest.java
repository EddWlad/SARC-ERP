package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.TicketStateAt.Span;

/** FR-073, FR-074 y los escenarios 27 a 30: pertenencia a un periodo y estado al final de el. */
class TicketStateAtTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final Instant OCT_START = day(2026, 10, 1);
	private static final Instant NOV_START = day(2026, 11, 1);
	private static final Instant DEC_START = day(2026, 12, 1);
	private static final Instant CUT_OFF = day(2026, 11, 22).plusSeconds(15 * 3600);

	private static Instant day(int year, int month, int day) {
		return LocalDate.of(year, month, day).atStartOfDay(ZONE).toInstant();
	}

	private static List<Span> one(Instant t0, Instant t4) {
		return List.of(new Span(t0, t4));
	}

	@Test
	void aTicketOpenOnOct25AndClosedOnNov3IsOpenInOctoberAndClosedInNovember() {
		List<Span> spans = one(day(2026, 10, 25), day(2026, 11, 3));

		assertThat(TicketStateAt.includedIn(spans, OCT_START, NOV_START)).isTrue();
		assertThat(TicketStateAt.at(spans, NOV_START)).as("fin de octubre").isEqualTo(TicketState.OPEN);
		assertThat(TicketStateAt.includedIn(spans, NOV_START, TicketStateAt.effectiveEnd(DEC_START, CUT_OFF))).isTrue();
		assertThat(TicketStateAt.at(spans, TicketStateAt.effectiveEnd(DEC_START, CUT_OFF))).as("noviembre")
				.isEqualTo(TicketState.CLOSED);
		assertThat(TicketStateAt.at(spans, CUT_OFF)).as("todo el historial").isEqualTo(TicketState.CLOSED);
	}

	@Test
	void aTicketClosedAndReopenedCountsOnceWithTheStateOfTheEpisodeInForce() {
		List<Span> spans = List.of(new Span(day(2026, 10, 15), day(2026, 10, 20)), new Span(day(2026, 11, 5), null));

		assertThat(TicketStateAt.includedIn(spans, OCT_START, NOV_START)).isTrue();
		assertThat(TicketStateAt.at(spans, NOV_START)).as("octubre: el episodio 1 ya cerro").isEqualTo(TicketState.CLOSED);
		assertThat(TicketStateAt.at(spans, CUT_OFF)).as("noviembre: el episodio 2 sigue abierto")
				.isEqualTo(TicketState.OPEN);
	}

	@Test
	void aClosureExactlyAtTheExclusiveEndKeepsTheTicketOpen() {
		List<Span> spans = one(day(2026, 10, 20), NOV_START);

		assertThat(TicketStateAt.at(spans, NOV_START)).isEqualTo(TicketState.OPEN);
		assertThat(TicketStateAt.at(spans, NOV_START.plusSeconds(1))).isEqualTo(TicketState.CLOSED);
	}

	@Test
	void theStartIsInclusiveAndTheEndExclusiveForMembership() {
		assertThat(TicketStateAt.includedIn(one(day(2026, 10, 1), OCT_START), OCT_START, NOV_START))
				.as("T4 igual al inicio: estuvo abierto en el rango").isTrue();
		assertThat(TicketStateAt.includedIn(one(day(2026, 9, 1), OCT_START.minusSeconds(1)), OCT_START, NOV_START))
				.as("cerro antes del inicio").isFalse();
		assertThat(TicketStateAt.includedIn(one(NOV_START, null), OCT_START, NOV_START))
				.as("T0 igual al fin exclusivo: todavia no existia").isFalse();
		assertThat(TicketStateAt.includedIn(one(NOV_START.minusSeconds(1), null), OCT_START, NOV_START)).isTrue();
	}

	@Test
	void anOpenTicketFromBeforeThePeriodIsIncludedAndOpen() {
		List<Span> spans = one(day(2026, 8, 3), null);

		assertThat(TicketStateAt.includedIn(spans, OCT_START, NOV_START)).isTrue();
		assertThat(TicketStateAt.at(spans, NOV_START)).isEqualTo(TicketState.OPEN);
	}

	@Test
	void anEndAfterTheCutOffUsesTheCutOff() {
		assertThat(TicketStateAt.effectiveEnd(DEC_START, CUT_OFF)).as("el corte es inclusivo: un instante despues")
				.isEqualTo(CUT_OFF.plusNanos(1));
		assertThat(TicketStateAt.effectiveEnd(NOV_START, CUT_OFF)).isEqualTo(NOV_START);

		List<Span> closedAfterCut = one(day(2026, 11, 10), day(2026, 11, 25));
		assertThat(TicketStateAt.at(closedAfterCut, TicketStateAt.effectiveEnd(DEC_START, CUT_OFF)))
				.as("mes en curso: lo cerrado despues del corte no se conocia, pero el corte lo ve abierto")
				.isEqualTo(TicketState.OPEN);
	}

	@Test
	void aTicketOpenedExactlyAtTheCutOffIsIncludedAndOpen() {
		Instant end = TicketStateAt.effectiveEnd(DEC_START, CUT_OFF);
		List<Span> spans = one(CUT_OFF, null);

		assertThat(TicketStateAt.includedIn(spans, NOV_START, end)).isTrue();
		assertThat(TicketStateAt.at(spans, end)).isEqualTo(TicketState.OPEN);
		assertThat(TicketStateAt.at(one(day(2026, 11, 20), CUT_OFF), end)).as("cerrado en el mismo instante del corte")
				.isEqualTo(TicketState.CLOSED);
		assertThat(TicketStateAt.includedIn(one(NOV_START, null), OCT_START, NOV_START))
				.as("un fin que no es el corte sigue siendo exclusivo").isFalse();
	}

	@Test
	void aTicketCreatedAfterThePeriodIsNotIncluded() {
		assertThat(TicketStateAt.includedIn(one(day(2026, 11, 10), null), OCT_START, NOV_START)).isFalse();
	}
}
