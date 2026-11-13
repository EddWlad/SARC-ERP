package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Estimate;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Input;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.Point;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.AvailabilityEstimator.PointResult;

/**
 * Disponibilidad estimada de prueba (FR-042 a FR-046a): el criterio de Enerlink en minutos, ponderado, con la
 * cobertura de los dias revisados y sin presentar nunca un dia sin datos como 100 %.
 */
class AvailabilityEstimatorTest {

	private static final LocalDate OCT_5 = LocalDate.parse("2026-10-05");
	private static final LocalDate OCT_6 = LocalDate.parse("2026-10-06");
	private static final LocalDate OCT_7 = LocalDate.parse("2026-10-07");

	private final AvailabilityEstimator estimator = new AvailabilityEstimator();

	private static Point point(String name, Set<LocalDate> blocked, Map<LocalDate, Long> offline) {
		return point(name, blocked, offline, LocalDate.MIN);
	}

	private static Point point(String name, Set<LocalDate> blocked, Map<LocalDate, Long> offline, LocalDate existsFrom) {
		return new Point(UUID.nameUUIDFromBytes(name.getBytes()), "9" + Math.abs(name.hashCode() % 100000), name,
				blocked, offline, existsFrom);
	}

	private static Point clean(String name) {
		return point(name, Set.of(), Map.of());
	}

	private static Input input(String from, String to, String today, Set<LocalDate> reviewed,
			Map<LocalDate, Integer> pendingLink, List<Point> points) {
		return new Input(LocalDate.parse(from), LocalDate.parse(to), LocalDate.parse(today), reviewed, pendingLink, points);
	}

	private static PointResult result(Estimate estimate, String name) {
		return estimate.points().stream().filter(point -> point.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void tenPointsOneReviewedDayAndOnePointWith72Minutes() {
		List<Point> points = new ArrayList<>();
		points.add(point("Punto 0", Set.of(), Map.of(OCT_5, 72L)));
		for (int index = 1; index < 10; index++) {
			points.add(clean("Punto " + index));
		}

		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-06", "2026-10-06", Set.of(OCT_5), Map.of(), points));

		assertThat(result(estimate, "Punto 0").percent()).isEqualByComparingTo("95.00");
		assertThat(result(estimate, "Punto 0").evaluatedMinutes()).isEqualTo(1_440);
		assertThat(estimate.points().stream().filter(point -> !point.name().equals("Punto 0")))
				.hasSize(9).allSatisfy(point -> assertThat(point.percent()).isEqualByComparingTo("100.00"));
		assertThat(estimate.aggregate().evaluatedMinutes()).isEqualTo(14_400);
		assertThat(estimate.aggregate().offlineMinutes()).isEqualTo(72);
		assertThat(estimate.aggregate().percent()).isEqualByComparingTo("99.50");
		assertThat(estimate.coverage().coveredDays()).isEqualTo(1);
		assertThat(estimate.coverage().daysInScope()).isEqualTo(1);
	}

	@Test
	void aDayWithoutAMarkAddsNoEvaluatedTimeAndIsNeverOneHundredPercent() {
		Estimate estimate = estimator.estimate(
				input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5), Map.of(), List.of(clean("A"))));

		assertThat(estimate.coverage().coveredDays()).isEqualTo(1);
		assertThat(estimate.coverage().daysInScope()).isEqualTo(2);
		assertThat(result(estimate, "A").evaluatedMinutes()).isEqualTo(1_440);
	}

	@Test
	void aPointWithoutCoveredDaysHasNoDataAndGoesLast() {
		Point withData = point("Con datos", Set.of(), Map.of(OCT_5, 30L));
		Point blocked = point("Bloqueado", Set.of(OCT_5), Map.of());

		Estimate estimate = estimator.estimate(
				input("2026-10-05", "2026-10-06", "2026-10-06", Set.of(OCT_5), Map.of(), List.of(blocked, withData)));

		PointResult noData = result(estimate, "Bloqueado");
		assertThat(noData.percent()).isNull();
		assertThat(noData.evaluatedMinutes()).isZero();
		assertThat(noData.coveredDays()).isZero();
		assertThat(estimate.points().getLast().name()).isEqualTo("Bloqueado");
		// Sin dias marcados en absoluto, tampoco hay agregado.
		Estimate unreviewed = estimator
				.estimate(input("2026-10-05", "2026-10-06", "2026-10-06", Set.of(), Map.of(), List.of(clean("A"))));
		assertThat(unreviewed.aggregate().percent()).isNull();
		assertThat(unreviewed.points()).singleElement().satisfies(point -> assertThat(point.percent()).isNull());
	}

	@Test
	void todayAndFutureDaysNeitherCountAsCoveredNorAsWithoutData() {
		// A mitad de quincena, con todos los dias terminados marcados, la cobertura es total.
		Set<LocalDate> finished = Set.of(OCT_5, OCT_6);

		Estimate estimate = estimator.estimate(
				input("2026-10-05", "2026-10-16", "2026-10-07", finished, Map.of(), List.of(clean("A"))));

		assertThat(estimate.coverage().daysInScope()).isEqualTo(2);
		assertThat(estimate.coverage().coveredDays()).isEqualTo(2);
		// Marcar hoy no lo vuelve cubierto.
		Estimate markedToday = estimator.estimate(input("2026-10-05", "2026-10-16", "2026-10-07",
				Set.of(OCT_5, OCT_6, OCT_7), Map.of(), List.of(clean("A"))));
		assertThat(markedToday.coverage().coveredDays()).isEqualTo(2);
	}

	@Test
	void aPendingLinkRowBlocksTheDayForEveryPoint() {
		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5, OCT_6),
				Map.of(OCT_5, 2), List.of(clean("A"), clean("B"))));

		assertThat(estimate.coverage().coveredDays()).isEqualTo(1);
		assertThat(estimate.blockedDays()).singleElement().satisfies(day -> {
			assertThat(day.day()).isEqualTo(OCT_5);
			assertThat(day.pendingRows()).isEqualTo(2);
		});
		assertThat(estimate.points()).allSatisfy(point -> assertThat(point.coveredDays()).isEqualTo(1));
	}

	@Test
	void aBlockedPointDayOnlyLeavesItsOwnPointWithoutCoverage() {
		Point conflict = point("En conflicto", Set.of(OCT_5), Map.of());

		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-07", "2026-10-10",
				Set.of(OCT_5, OCT_6), Map.of(), List.of(conflict, clean("Libre"))));

		assertThat(result(estimate, "En conflicto").coveredDays()).isEqualTo(1);
		assertThat(result(estimate, "Libre").coveredDays()).isEqualTo(2);
		assertThat(estimate.aggregate().evaluatedMinutes()).isEqualTo(3L * 1_440);
		assertThat(estimate.coverage().coveredDays()).isEqualTo(2);
	}

	@Test
	void offlineTimeOutsideTheCoveredDaysIsNotDiscounted() {
		Point point = point("A", Set.of(), Map.of(OCT_5, 100L, OCT_6, 500L));

		Estimate estimate = estimator.estimate(
				input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5), Map.of(), List.of(point)));

		assertThat(result(estimate, "A").offlineMinutes()).isEqualTo(100);
		assertThat(result(estimate, "A").percent()).isEqualByComparingTo("93.06");
	}

	@Test
	void theTableGoesFromLowestToHighestThenMoreOfflineMinutesThenName() {
		Point worst = point("Peor", Set.of(), Map.of(OCT_5, 400L));
		Point sameLessOffline = point("Igual B", Set.of(), Map.of(OCT_5, 100L));
		Point first = point("Alfa", Set.of(), Map.of(OCT_5, 0L));
		Point second = point("Beta", Set.of(), Map.of());

		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-06", "2026-10-06", Set.of(OCT_5), Map.of(),
				List.of(second, sameLessOffline, first, worst)));

		assertThat(estimate.points()).extracting(PointResult::name).containsExactly("Peor", "Igual B", "Alfa", "Beta");
	}

	@Test
	void theNameOrderIgnoresCaseAndAccentsAtTheSamePercentAndMinutes() {
		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-06", "2026-10-06", Set.of(OCT_5), Map.of(),
				List.of(clean("zeta"), clean("Árbol"), clean("beta"))));

		assertThat(estimate.points()).extracting(PointResult::name).containsExactly("Árbol", "beta", "zeta");
	}

	@Test
	void roundsHalfUpToTwoDecimalsAndWeightsByMinutesNotByAveragingPercentages() {
		// Un punto con 2 dias cubiertos y otro con 1: el agregado pesa por minutos.
		Point two = point("Dos dias", Set.of(), Map.of(OCT_5, 1_440L));
		Point one = point("Un dia", Set.of(OCT_6), Map.of());

		Estimate estimate = estimator.estimate(
				input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5, OCT_6), Map.of(), List.of(two, one)));

		// Dos dias: un dia completo fuera de linea (50,00 %); un dia: 100 %. Ponderado: (4.320 - 1.440) / 4.320 = 66,67 %, no el promedio de 50 y 100.
		assertThat(result(estimate, "Dos dias").percent()).isEqualByComparingTo("50.00");
		assertThat(result(estimate, "Un dia").percent()).isEqualByComparingTo("100.00");
		assertThat(estimate.aggregate().evaluatedMinutes()).isEqualTo(4_320);
		assertThat(estimate.aggregate().percent()).isEqualByComparingTo(new BigDecimal("66.67"));
		assertThat(AvailabilityEstimator.percent(1_440, 1)).isEqualByComparingTo("99.93");
		assertThat(AvailabilityEstimator.percent(0, 0)).isNull();
	}

	@Test
	void aPointIsNotEvaluatedBeforeItExistedNotEvenAsOneHundredPercent() {
		Point old = point("Antiguo", Set.of(), Map.of());
		Point recent = point("Nuevo", Set.of(), Map.of(OCT_6, 144L), OCT_6);

		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5, OCT_6),
				Map.of(), List.of(old, recent)));

		assertThat(result(estimate, "Antiguo").coveredDays()).isEqualTo(2);
		assertThat(result(estimate, "Nuevo").coveredDays()).isEqualTo(1);
		assertThat(result(estimate, "Nuevo").evaluatedMinutes()).isEqualTo(1_440);
		assertThat(result(estimate, "Nuevo").percent()).isEqualByComparingTo("90.00");
		// El agregado pesa solo los dias en que cada punto existia: 2.880 + 1.440 minutos, 144 fuera de linea.
		assertThat(estimate.aggregate().evaluatedMinutes()).isEqualTo(4_320);
		assertThat(estimate.aggregate().percent()).isEqualByComparingTo("96.67");
		// La cobertura de la red no cambia: se mide sobre los dias revisados.
		assertThat(estimate.coverage().coveredDays()).isEqualTo(2);
	}

	@Test
	void aPointCreatedAfterEveryReviewedDayHasNoDataAndStillGoesLast() {
		Point future = point("Muy nuevo", Set.of(), Map.of(), OCT_7);

		Estimate estimate = estimator.estimate(input("2026-10-05", "2026-10-07", "2026-10-10", Set.of(OCT_5, OCT_6),
				Map.of(), List.of(future, clean("Antiguo"))));

		assertThat(result(estimate, "Muy nuevo").percent()).isNull();
		assertThat(result(estimate, "Muy nuevo").coveredDays()).isZero();
		assertThat(estimate.points().getLast().name()).isEqualTo("Muy nuevo");
	}
}
