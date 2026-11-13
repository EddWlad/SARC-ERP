package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Disponibilidad estimada de prueba (FR-042 a FR-046a), pura: con el criterio de Enerlink, {@code (evaluado -
 * fuera de linea) / evaluado}, en minutos y ponderada, nunca promediando porcentajes. El tiempo evaluado es
 * 1.440 minutos por dia cubierto. Un dia solo cuenta si se marco como revisado, ya termino y nada lo deja sin
 * cubrir; un dia sin cobertura no suma tiempo evaluado y nunca se presenta como 100 %.
 */
public final class AvailabilityEstimator {

	/** Minutos de un dia: el horario de atencion del inventario no reduce el tiempo evaluado (FR-043). */
	public static final int MINUTES_PER_DAY = 1_440;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * Un punto en alcance: {@code blockedDays} son los dias que no se pueden dar por cubiertos para este punto
	 * (ventanas por revisar o abiertas, filas en conflicto), {@code offlineByDay} los minutos confirmados fuera
	 * de linea de cada dia y {@code existsFrom} el primer dia completo en que el punto ya existia: antes no
	 * cuenta, ni como 100 % (FR-045).
	 */
	public record Point(UUID equipmentId, String code, String name, Set<LocalDate> blockedDays,
			Map<LocalDate, Long> offlineByDay, LocalDate existsFrom) {
	}

	/**
	 * {@code [from, to)} es el rango consultado y {@code today} el dia del corte, que no cuenta. {@code
	 * reviewedDays} son los dias con marca vigente y {@code pendingLinkRowsByDay} las filas por vincular de
	 * lotes confirmados que tocan cada dia: dejan el dia sin cubrir para todos los puntos.
	 */
	public record Input(LocalDate from, LocalDate to, LocalDate today, Set<LocalDate> reviewedDays,
			Map<LocalDate, Integer> pendingLinkRowsByDay, List<Point> points) {
	}

	/** {@code percent} es nulo cuando no hay tiempo evaluado: «Sin datos». */
	public record Aggregate(BigDecimal percent, long evaluatedMinutes, long offlineMinutes) {
	}

	/** Dias cubiertos sobre dias en alcance: los dias del rango ya terminados hasta el corte (FR-046). */
	public record Coverage(int coveredDays, int daysInScope) {
	}

	public record BlockedDay(LocalDate day, int pendingRows) {
	}

	public record PointResult(UUID equipmentId, String code, String name, BigDecimal percent, long evaluatedMinutes,
			long offlineMinutes, int coveredDays) {
	}

	public record Estimate(Aggregate aggregate, Coverage coverage, List<BlockedDay> blockedDays,
			List<PointResult> points) {
	}

	public Estimate estimate(Input input) {
		List<LocalDate> inScope = new ArrayList<>();
		for (LocalDate day = input.from(); day.isBefore(input.to()) && day.isBefore(input.today()); day = day.plusDays(1)) {
			inScope.add(day);
		}
		Map<LocalDate, Integer> blocked = new TreeMap<>();
		List<LocalDate> covered = new ArrayList<>();
		for (LocalDate day : inScope) {
			int pending = input.pendingLinkRowsByDay().getOrDefault(day, 0);
			if (pending > 0) {
				blocked.put(day, pending);
			}
			else if (input.reviewedDays().contains(day)) {
				covered.add(day);
			}
		}

		List<PointResult> results = new ArrayList<>();
		long evaluated = 0;
		long offline = 0;
		for (Point point : input.points()) {
			Set<LocalDate> days = new HashSet<>(covered);
			days.removeAll(point.blockedDays());
			days.removeIf(day -> day.isBefore(point.existsFrom()));
			long pointEvaluated = (long) MINUTES_PER_DAY * days.size();
			long pointOffline = 0;
			for (LocalDate day : days) {
				pointOffline += point.offlineByDay().getOrDefault(day, 0L);
			}
			results.add(new PointResult(point.equipmentId(), point.code(), point.name(),
					percent(pointEvaluated, pointOffline), pointEvaluated, pointOffline, days.size()));
			evaluated += pointEvaluated;
			offline += pointOffline;
		}
		results.sort(order());

		List<BlockedDay> blockedDays = blocked.entrySet().stream()
				.map(entry -> new BlockedDay(entry.getKey(), entry.getValue())).toList();
		return new Estimate(new Aggregate(percent(evaluated, offline), evaluated, offline),
				new Coverage(covered.size(), inScope.size()), blockedDays, List.copyOf(results));
	}

	/** {@code (evaluado - fuera) / evaluado * 100} con dos decimales; sin tiempo evaluado, nulo. */
	static BigDecimal percent(long evaluatedMinutes, long offlineMinutes) {
		if (evaluatedMinutes <= 0) {
			return null;
		}
		return BigDecimal.valueOf(evaluatedMinutes - offlineMinutes).multiply(HUNDRED)
				.divide(BigDecimal.valueOf(evaluatedMinutes), 2, RoundingMode.HALF_UP);
	}

	/**
	 * De menor a mayor disponibilidad; entre iguales, mas minutos fuera de linea y luego el nombre actual sin
	 * distinguir mayusculas ni tildes. Los puntos sin dias cubiertos van al final (FR-046a).
	 */
	private static Comparator<PointResult> order() {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("es-EC"));
		collator.setStrength(Collator.PRIMARY);
		return Comparator.comparing(PointResult::percent, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
				.thenComparing(Comparator.comparingLong(PointResult::offlineMinutes).reversed())
				.thenComparing(PointResult::name, collator).thenComparing(PointResult::equipmentId);
	}
}
