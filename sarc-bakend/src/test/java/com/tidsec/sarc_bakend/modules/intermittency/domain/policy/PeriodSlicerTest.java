package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer.DaySlice;

class PeriodSlicerTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

	private final PeriodSlicer slicer = new PeriodSlicer(ZONE);

	private static Instant at(String local) {
		return LocalDateTime.parse(local).atZone(ZONE).toInstant();
	}

	private static Instant day(String date) {
		return LocalDate.parse(date).atStartOfDay(ZONE).toInstant();
	}

	@Test
	void splitsAWindowThatCrossesMidnightWithoutDuplicatingIt() {
		Instant start = at("2026-10-15T23:50");
		Instant end = at("2026-10-16T00:20");

		List<DaySlice> slices = slicer.byDay(start, end, day("2026-10-01"), day("2026-11-01"), at("2026-11-05T00:00"));

		assertThat(slices).containsExactly(new DaySlice(LocalDate.of(2026, 10, 15), 10),
				new DaySlice(LocalDate.of(2026, 10, 16), 20));
		assertThat(slices.stream().mapToLong(DaySlice::minutes).sum()).isEqualTo(30);
	}

	@Test
	void rowWithTheNextDayEndSplitsIntoItsTwoDays() {
		List<DaySlice> slices = slicer.byDay(at("2026-10-12T20:03"), at("2026-10-13T12:22"), day("2026-10-01"),
				day("2026-11-01"), at("2026-11-05T00:00"));

		assertThat(slices).extracting(DaySlice::minutes).containsExactly(237L, 742L);
	}

	@Test
	void givesEachFortnightOnlyItsOwnShareWhenAWindowCrossesTheBoundary() {
		Instant start = at("2026-10-15T23:45");
		Instant end = at("2026-10-16T00:30");
		Instant cutOff = at("2026-11-05T00:00");

		long firstHalf = slicer.minutesWithin(start, end, day("2026-10-01"), day("2026-10-16"), cutOff);
		long secondHalf = slicer.minutesWithin(start, end, day("2026-10-16"), day("2026-11-01"), cutOff);

		assertThat(firstHalf).isEqualTo(15);
		assertThat(secondHalf).isEqualTo(30);
		assertThat(firstHalf + secondHalf).isEqualTo(45);
	}

	@Test
	void splitsAtTheEndOfAMonth() {
		Instant start = at("2026-10-31T23:00");
		Instant end = at("2026-11-01T01:00");
		Instant cutOff = at("2026-11-05T00:00");

		assertThat(slicer.minutesWithin(start, end, day("2026-10-01"), day("2026-11-01"), cutOff)).isEqualTo(60);
		assertThat(slicer.minutesWithin(start, end, day("2026-11-01"), day("2026-12-01"), cutOff)).isEqualTo(60);
	}

	@Test
	void handlesALeapYear() {
		List<DaySlice> slices = slicer.byDay(at("2028-02-28T23:00"), at("2028-03-01T01:00"), day("2028-02-01"),
				day("2028-04-01"), at("2028-04-10T00:00"));

		assertThat(slices).containsExactly(new DaySlice(LocalDate.of(2028, 2, 28), 60),
				new DaySlice(LocalDate.of(2028, 2, 29), 1440), new DaySlice(LocalDate.of(2028, 3, 1), 60));
	}

	@Test
	void splitsAWindowOfThreeDaysAcrossFourLocalDays() {
		List<DaySlice> slices = slicer.byDay(at("2026-10-10T12:00"), at("2026-10-13T06:00"), day("2026-10-01"),
				day("2026-11-01"), at("2026-11-05T00:00"));

		assertThat(slices).extracting(DaySlice::minutes).containsExactly(720L, 1440L, 1440L, 360L);
		assertThat(slices.stream().mapToLong(DaySlice::minutes).sum()).isEqualTo(66 * 60);
	}

	@Test
	void anOpenWindowIsCutAtTheCutOffOrTheEndOfThePeriod() {
		Instant start = at("2026-10-14T10:00");
		Instant cutOff = at("2026-10-15T12:00");

		assertThat(slicer.byDay(start, null, day("2026-10-01"), day("2026-10-16"), cutOff))
				.containsExactly(new DaySlice(LocalDate.of(2026, 10, 14), 840),
						new DaySlice(LocalDate.of(2026, 10, 15), 720));
		// El periodo termina antes que el corte: manda el fin del periodo.
		assertThat(slicer.minutesWithin(start, null, day("2026-10-01"), day("2026-10-15"), cutOff)).isEqualTo(840);
		// Una ventana abierta que empieza despues del corte no aporta nada.
		assertThat(slicer.minutesWithin(at("2026-10-16T08:00"), null, day("2026-10-01"), day("2026-11-01"), cutOff))
				.isZero();
	}

	@Test
	void aWindowOutsideThePeriodHasNoSlices() {
		Instant cutOff = at("2026-11-05T00:00");

		assertThat(slicer.minutesWithin(at("2026-09-30T10:00"), at("2026-09-30T11:00"), day("2026-10-01"),
				day("2026-10-16"), cutOff)).isZero();
		assertThat(slicer.byDay(at("2026-10-20T10:00"), at("2026-10-20T11:00"), day("2026-10-01"),
				day("2026-10-16"), cutOff)).isEmpty();
	}

	@Test
	void dayOfUsesTheOperatingZoneNotUtc() {
		// 23:30 en Guayaquil ya es el dia siguiente en UTC.
		assertThat(slicer.dayOf(at("2026-10-15T23:30"))).isEqualTo(LocalDate.of(2026, 10, 15));
		assertThat(slicer.startOf(LocalDate.of(2026, 10, 15))).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
	}
}
