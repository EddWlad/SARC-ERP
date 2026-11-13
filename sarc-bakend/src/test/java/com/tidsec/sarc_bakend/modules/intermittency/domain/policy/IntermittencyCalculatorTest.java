package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowSource;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.Figures;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.PointLabel;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.IntermittencyCalculator.TopPoint;

/** Indicadores del dashboard calculados a mano (FR-033 a FR-036, CA-005-01). */
class IntermittencyCalculatorTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

	private static final Instant NOW = at("2026-10-31", "12:00");

	private static final Instant OCT_1 = at("2026-10-01", "00:00");

	private static final Instant NOV_1 = at("2026-11-01", "00:00");

	private final IntermittencyCalculator calculator = new IntermittencyCalculator(new PeriodSlicer(ZONE));

	private static Instant at(String day, String time) {
		return LocalDate.parse(day).atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
	}

	private static IntermittencyWindow window(UUID point, String name, Instant start, Instant end) {
		return IntermittencyWindow.register(point, name, start, end, null, WindowSource.MANUAL, null, false, NOW);
	}

	/** Ventana confirmada; una de 240 minutos o mas nace por revisar y un editor la confirma. */
	private static IntermittencyWindow minutes(UUID point, String name, String day, String time, int minutes) {
		Instant start = at(day, time);
		IntermittencyWindow window = window(point, name, start, start.plusSeconds(minutes * 60L));
		if (window.getState() == WindowState.REVIEW) {
			window.confirmReview("Revisada", UUID.randomUUID(), NOW);
		}
		return window;
	}

	private Figures october(List<IntermittencyWindow> windows) {
		return calculator.calculate(windows, OCT_1, NOV_1, NOW, Map.of());
	}

	@Test
	void onlyConfirmedWindowsWithTimeInThePeriodAreOfficial() {
		UUID point = UUID.randomUUID();
		IntermittencyWindow confirmed = minutes(point, "A", "2026-10-05", "08:00", 30);
		IntermittencyWindow outsidePeriod = minutes(point, "A", "2026-09-05", "08:00", 30);
		IntermittencyWindow open = window(point, "A", at("2026-10-30", "08:00"), null);
		IntermittencyWindow review = window(point, "A", at("2026-10-06", "08:00"), at("2026-10-06", "13:00"));
		IntermittencyWindow test = IntermittencyWindow.register(point, "A", at("2026-10-07", "08:00"),
				at("2026-10-07", "09:00"), null, WindowSource.MANUAL, null, true, NOW);
		IntermittencyWindow annulled = minutes(point, "A", "2026-10-08", "08:00", 30);
		annulled.annul("duplicada", UUID.randomUUID(), NOW);

		Figures figures = october(List.of(confirmed, outsidePeriod, open, review, test, annulled));

		assertThat(figures.official().minutes()).isEqualTo(30);
		assertThat(figures.official().windows()).isEqualTo(1);
		assertThat(figures.unofficial().review()).isEqualTo(1);
		assertThat(figures.unofficial().test()).isEqualTo(1);
		assertThat(figures.unofficial().open().count()).isEqualTo(1);
		// La provisional de la abierta va aparte: del inicio al corte (30 oct 08:00 a 31 oct 12:00 = 28 h).
		assertThat(figures.unofficial().open().provisionalMinutes()).isEqualTo(28 * 60);
	}

	@Test
	void countsAffectedPointsWindowsPointDaysRecurrentPointsAndTheAverage() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		List<IntermittencyWindow> windows = List.of(minutes(a, "A", "2026-10-05", "08:00", 10),
				minutes(a, "A", "2026-10-06", "08:00", 20), minutes(a, "A", "2026-10-07", "08:00", 30),
				minutes(a, "A", "2026-10-07", "15:00", 10), minutes(b, "B", "2026-10-05", "08:00", 5),
				minutes(b, "B", "2026-10-06", "08:00", 6));

		Figures figures = october(windows);

		assertThat(figures.official().minutes()).isEqualTo(81);
		assertThat(figures.official().affectedPoints()).isEqualTo(2);
		assertThat(figures.official().windows()).isEqualTo(6);
		assertThat(figures.official().pointDays()).isEqualTo(5);
		// A tiene tiempo en 3 dias distintos y es recurrente; B, con 2, no.
		assertThat(figures.official().recurrentPoints()).isEqualTo(1);
		assertThat(figures.official().averageMinutes()).isEqualByComparingTo(new BigDecimal("13.5"));
		assertThat(figures.recurrence().recurrentMinutes()).isEqualTo(70);
		assertThat(figures.recurrence().nonRecurrentMinutes()).isEqualTo(11);
	}

	@Test
	void theTopIsOrderedByTimeNotByNumberOfWindows() {
		UUID long4 = UUID.randomUUID();
		UUID many12 = UUID.randomUUID();
		List<IntermittencyWindow> windows = new ArrayList<>();
		// A: 36:42 (2.202 min) en 4 ventanas.
		windows.add(minutes(long4, "A", "2026-10-02", "08:00", 600));
		windows.add(minutes(long4, "A", "2026-10-03", "08:00", 600));
		windows.add(minutes(long4, "A", "2026-10-04", "08:00", 600));
		windows.add(minutes(long4, "A", "2026-10-05", "08:00", 402));
		// B: 12 ventanas de 10 minutos.
		for (int day = 10; day < 22; day++) {
			windows.add(minutes(many12, "B", "2026-10-" + day, "08:00", 10));
		}

		Figures figures = october(windows);

		assertThat(figures.top()).extracting(TopPoint::equipmentId).containsExactly(long4, many12);
		assertThat(figures.top().get(0).minutes()).isEqualTo(2202);
		assertThat(figures.top().get(0).windows()).isEqualTo(4);
		assertThat(figures.top().get(1).windows()).isEqualTo(12);
		assertThat(figures.topExtendedByTies()).isFalse();
	}

	@Test
	void everyoneTiedAtTenthPlaceIsIncludedAndOrderedByWindowsDaysAndName() {
		List<IntermittencyWindow> windows = new ArrayList<>();
		Map<UUID, PointLabel> labels = new java.util.HashMap<>();
		// Nueve puntos con tiempos distintos y mayores: 1.000, 900, ... 200 minutos.
		for (int rank = 0; rank < 9; rank++) {
			UUID id = UUID.randomUUID();
			labels.put(id, new PointLabel("90000" + rank, "Punto " + rank));
			windows.add(minutes(id, "Punto " + rank, "2026-10-02", "08:00", 1000 - 100 * rank));
		}
		// Tres empatados con 60 minutos en el puesto 10: distinto numero de ventanas, dias y nombre.
		UUID threeWindows = UUID.randomUUID();
		UUID twoWindowsZeta = UUID.randomUUID();
		UUID twoWindowsAlfa = UUID.randomUUID();
		labels.put(threeWindows, new PointLabel("900101", "Tres ventanas"));
		labels.put(twoWindowsZeta, new PointLabel("900102", "zeta"));
		labels.put(twoWindowsAlfa, new PointLabel("900103", "Álvaro"));
		for (int index = 0; index < 3; index++) {
			windows.add(minutes(threeWindows, "x", "2026-10-1" + index, "08:00", 20));
		}
		windows.add(minutes(twoWindowsZeta, "x", "2026-10-20", "08:00", 30));
		windows.add(minutes(twoWindowsZeta, "x", "2026-10-21", "08:00", 30));
		windows.add(minutes(twoWindowsAlfa, "x", "2026-10-20", "09:00", 30));
		windows.add(minutes(twoWindowsAlfa, "x", "2026-10-21", "09:00", 30));
		// Con 59 minutos queda fuera: no empata con el decimo puesto.
		UUID outside = UUID.randomUUID();
		labels.put(outside, new PointLabel("900104", "Fuera"));
		windows.add(minutes(outside, "x", "2026-10-25", "08:00", 59));

		Figures figures = calculator.calculate(windows, OCT_1, NOV_1, NOW, labels);

		assertThat(figures.top()).hasSize(12);
		assertThat(figures.topExtendedByTies()).isTrue();
		assertThat(figures.top()).extracting(TopPoint::equipmentId).doesNotContain(outside);
		// Empate: mas ventanas primero; con las mismas ventanas y dias, el nombre sin distinguir tildes.
		assertThat(figures.top().subList(9, 12)).extracting(TopPoint::name).containsExactly("Tres ventanas",
				"Álvaro", "zeta");
		assertThat(figures.concentration().topMinutes()).isEqualTo(figures.top().stream().mapToLong(TopPoint::minutes).sum());
		assertThat(figures.concentration().restMinutes()).isEqualTo(59);
	}

	@Test
	void withFewerThanTenAffectedTheAvailableOnesAreShown() {
		List<IntermittencyWindow> windows = new ArrayList<>();
		for (int index = 0; index < 4; index++) {
			windows.add(minutes(UUID.randomUUID(), "P" + index, "2026-10-02", "08:00", 10 + index));
		}

		Figures figures = october(windows);

		assertThat(figures.top()).hasSize(4);
		assertThat(figures.topExtendedByTies()).isFalse();
	}

	@Test
	void durationBandLimitsBelongToTheBandAbove() {
		assertThat(DurationBand.of(4)).isEqualTo(DurationBand.UNDER_5);
		assertThat(DurationBand.of(5)).isEqualTo(DurationBand.FROM_5_TO_14);
		assertThat(DurationBand.of(14)).isEqualTo(DurationBand.FROM_5_TO_14);
		assertThat(DurationBand.of(15)).isEqualTo(DurationBand.FROM_15_TO_59);
		assertThat(DurationBand.of(59)).isEqualTo(DurationBand.FROM_15_TO_59);
		assertThat(DurationBand.of(60)).isEqualTo(DurationBand.FROM_60_TO_239);
		assertThat(DurationBand.of(239)).isEqualTo(DurationBand.FROM_60_TO_239);
		assertThat(DurationBand.of(240)).isEqualTo(DurationBand.FROM_240);
	}

	@Test
	void aWindowThatCrossesTheFortnightKeepsItsBandInBothPeriodsAndEachPieAddsUpToTheOfficialTotal() {
		UUID point = UUID.randomUUID();
		// 30 minutos: 15 caen el 15 y 15 el 16. Dura 30, asi que va en [15, 60) en ambas quincenas.
		IntermittencyWindow crossing = window(point, "A", at("2026-10-15", "23:45"), at("2026-10-16", "00:15"));
		IntermittencyWindow other = minutes(UUID.randomUUID(), "B", "2026-10-03", "08:00", 3);

		Figures first = calculator.calculate(List.of(crossing, other), OCT_1, at("2026-10-16", "00:00"), NOW,
				Map.of());
		Figures second = calculator.calculate(List.of(crossing, other), at("2026-10-16", "00:00"), NOV_1, NOW,
				Map.of());

		assertThat(first.official().minutes()).isEqualTo(18);
		assertThat(second.official().minutes()).isEqualTo(15);
		assertThat(share(first, DurationBand.FROM_15_TO_59)).isEqualTo(15);
		assertThat(share(second, DurationBand.FROM_15_TO_59)).isEqualTo(15);
		assertThat(share(first, DurationBand.UNDER_5)).isEqualTo(3);
		for (Figures figures : List.of(first, second)) {
			assertThat(figures.byDurationBand().stream().mapToLong(IntermittencyCalculator.BandShare::minutes).sum())
					.isEqualTo(figures.official().minutes());
			assertThat(figures.concentration().topMinutes() + figures.concentration().restMinutes())
					.isEqualTo(figures.official().minutes());
			assertThat(figures.recurrence().recurrentMinutes() + figures.recurrence().nonRecurrentMinutes())
					.isEqualTo(figures.official().minutes());
		}
	}

	private static long share(Figures figures, DurationBand band) {
		return figures.byDurationBand().stream().filter(share -> share.band() == band).findFirst().orElseThrow()
				.minutes();
	}

	@Test
	void aWindowAcrossMidnightCountsOnEachDayButOnlyOnceInTheTotal() {
		UUID point = UUID.randomUUID();

		Figures figures = october(List.of(window(point, "A", at("2026-10-14", "23:50"), at("2026-10-15", "00:20"))));

		assertThat(figures.official().windows()).isEqualTo(1);
		assertThat(figures.dailyWindowsMayExceedTotal()).isTrue();
		var day14 = figures.daily().stream().filter(day -> day.day().equals(LocalDate.parse("2026-10-14"))).findFirst()
				.orElseThrow();
		var day15 = figures.daily().stream().filter(day -> day.day().equals(LocalDate.parse("2026-10-15"))).findFirst()
				.orElseThrow();
		assertThat(day14.minutes()).isEqualTo(10);
		assertThat(day14.windows()).isEqualTo(1);
		assertThat(day15.minutes()).isEqualTo(20);
		assertThat(day15.windows()).isEqualTo(1);
		assertThat(figures.official().pointDays()).isEqualTo(2);
	}

	@Test
	void theDailySeriesFillsZerosOnlyUpToTheCutOffDay() {
		Figures figures = october(List.of(minutes(UUID.randomUUID(), "A", "2026-10-05", "08:00", 30)));

		// El corte es el 31 de octubre: toda la serie, con ceros los dias sin tiempo.
		assertThat(figures.daily()).hasSize(31);
		assertThat(figures.daily().get(4).minutes()).isEqualTo(30);
		assertThat(figures.daily().get(0).minutes()).isZero();
		assertThat(figures.dailyWindowsMayExceedTotal()).isFalse();

		Figures early = calculator.calculate(List.of(minutes(UUID.randomUUID(), "A", "2026-10-05", "08:00", 30)),
				OCT_1, NOV_1, at("2026-10-10", "09:00"), Map.of());
		assertThat(early.daily()).hasSize(10);
	}

	@Test
	void anEmptyPeriodHasCountsButNoSeriesOrPies() {
		UUID point = UUID.randomUUID();

		Figures figures = october(List.of(window(point, "A", at("2026-10-30", "08:00"), null)));

		assertThat(figures.official().minutes()).isZero();
		assertThat(figures.official().averageMinutes()).isNull();
		assertThat(figures.unofficial().open().count()).isEqualTo(1);
		assertThat(figures.daily()).isEmpty();
		assertThat(figures.top()).isEmpty();
		assertThat(figures.byDurationBand()).isEmpty();
	}
}
