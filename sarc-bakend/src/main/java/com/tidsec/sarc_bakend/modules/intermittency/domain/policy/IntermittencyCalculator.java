package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.IntermittencyWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.window.WindowState;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.PeriodSlicer.DaySlice;

/**
 * Indicadores del dashboard (FR-033 a FR-036), puros: reciben las ventanas que intersecan el periodo y nada
 * mas. Solo las CONFIRMED con tiempo dentro del periodo son oficiales; las abiertas, por revisar y de prueba
 * se cuentan aparte, y las anuladas no cuentan. Cada ventana aporta solo la porcion que cae en el periodo.
 */
public final class IntermittencyCalculator {

	public static final int TOP_SIZE = 10;

	/** Un punto es recurrente con tiempo confirmado en este numero de dias distintos o mas (RF-005.10). */
	public static final int RECURRENT_DAYS = 3;

	/** Codigo y nombre actuales de un punto; el nombre desempata el Top 10. */
	public record PointLabel(String code, String name) {
	}

	public record Official(long minutes, int affectedPoints, int windows, int pointDays, int recurrentPoints,
			BigDecimal averageMinutes) {
	}

	public record OpenFigures(int count, long provisionalMinutes) {
	}

	/** Ventanas que no cuentan en lo oficial, por estado. */
	public record Unofficial(OpenFigures open, int review, int test) {
	}

	public record TopPoint(UUID equipmentId, String code, String name, long minutes, int windows, int days) {
	}

	public record DailyPoint(LocalDate day, long minutes, int windows) {
	}

	public record BandShare(DurationBand band, long minutes, int windows) {
	}

	public record Concentration(long topMinutes, long restMinutes) {
	}

	public record Recurrence(long recurrentMinutes, long nonRecurrentMinutes) {
	}

	/**
	 * {@code byPoint} son todos los puntos con tiempo confirmado, en el orden del Top; {@code top} es su
	 * comienzo con los empatados en el decimo puesto.
	 */
	public record Figures(Official official, Unofficial unofficial, List<TopPoint> top, boolean topExtendedByTies,
			List<DailyPoint> daily, boolean dailyWindowsMayExceedTotal, List<BandShare> byDurationBand,
			Concentration concentration, Recurrence recurrence, List<TopPoint> byPoint) {
	}

	/** Una ventana confirmada con el tiempo que aporta al periodo. */
	public record WindowPortion(IntermittencyWindow window, long minutes) {
	}

	private static final class PointTotals {

		private final UUID equipmentId;

		private final String fallbackName;

		private long minutes;

		private int windows;

		private final Set<LocalDate> days = new HashSet<>();

		PointTotals(UUID equipmentId, String fallbackName) {
			this.equipmentId = equipmentId;
			this.fallbackName = fallbackName;
		}

		boolean recurrent() {
			return days.size() >= RECURRENT_DAYS;
		}
	}

	private final PeriodSlicer slicer;

	public IntermittencyCalculator(PeriodSlicer slicer) {
		this.slicer = slicer;
	}

	/**
	 * @param windows ventanas que intersecan {@code [from, to)}, en cualquier estado
	 * @param labels nombre y codigo actuales de los puntos; si falta alguno se usa la etiqueta registrada
	 * @param cutOff momento de la consulta: una ventana abierta cuenta provisionalmente hasta aqui
	 */
	public Figures calculate(Collection<IntermittencyWindow> windows, Instant from, Instant to, Instant cutOff,
			Map<UUID, PointLabel> labels) {
		long totalMinutes = 0;
		int officialWindows = 0;
		boolean crossesDays = false;
		int open = 0;
		long provisional = 0;
		int review = 0;
		int test = 0;
		Map<UUID, PointTotals> points = new HashMap<>();
		Map<LocalDate, long[]> daily = new TreeMap<>();
		Map<DurationBand, long[]> bands = new EnumMap<>(DurationBand.class);

		for (IntermittencyWindow window : windows) {
			switch (window.getState()) {
				case OPEN -> {
					if (window.getStartAt().isBefore(to)) {
						open++;
						provisional += slicer.minutesWithin(window.getStartAt(), null, from, to, cutOff);
					}
				}
				case REVIEW, TEST -> {
					if (slicer.minutesWithin(window.getStartAt(), window.getEndAt(), from, to, cutOff) > 0) {
						if (window.getState() == WindowState.REVIEW) {
							review++;
						}
						else {
							test++;
						}
					}
				}
				case CONFIRMED -> {
					List<DaySlice> slices = slicer.byDay(window.getStartAt(), window.getEndAt(), from, to, cutOff);
					if (slices.isEmpty()) {
						break;
					}
					long portion = 0;
					PointTotals point = points.computeIfAbsent(window.getEquipmentId(),
							id -> new PointTotals(id, window.getPointLabel()));
					for (DaySlice slice : slices) {
						portion += slice.minutes();
						long[] day = daily.computeIfAbsent(slice.day(), key -> new long[2]);
						day[0] += slice.minutes();
						day[1]++;
						point.days.add(slice.day());
					}
					point.minutes += portion;
					point.windows++;
					totalMinutes += portion;
					officialWindows++;
					crossesDays |= slices.size() > 1;
					long[] band = bands.computeIfAbsent(DurationBand.of(window.getDurationMinutes()),
							key -> new long[2]);
					band[0] += portion;
					band[1]++;
				}
				case ANNULLED -> {
				}
			}
		}

		Unofficial unofficial = new Unofficial(new OpenFigures(open, provisional), review, test);
		if (officialWindows == 0) {
			// Sin tiempo confirmado no hay series: ni dias en cero ni pasteles vacios.
			return new Figures(new Official(0, 0, 0, 0, 0, null), unofficial, List.of(), false, List.of(), false,
					List.of(), new Concentration(0, 0), new Recurrence(0, 0), List.of());
		}

		int pointDays = 0;
		int recurrentPoints = 0;
		long recurrentMinutes = 0;
		for (PointTotals point : points.values()) {
			pointDays += point.days.size();
			if (point.recurrent()) {
				recurrentPoints++;
				recurrentMinutes += point.minutes;
			}
		}
		Official official = new Official(totalMinutes, points.size(), officialWindows, pointDays, recurrentPoints,
				BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(officialWindows), 1, RoundingMode.HALF_UP));

		List<TopPoint> everyone = ranked(points.values(), labels);
		List<TopPoint> ranking = topOf(everyone);
		boolean extended = ranking.size() > TOP_SIZE;
		long topMinutes = ranking.stream().mapToLong(TopPoint::minutes).sum();

		List<BandShare> byBand = new ArrayList<>();
		for (DurationBand band : DurationBand.values()) {
			long[] share = bands.getOrDefault(band, new long[2]);
			byBand.add(new BandShare(band, share[0], (int) share[1]));
		}
		return new Figures(official, unofficial, ranking, extended, dailySeries(daily, from, to, cutOff),
				crossesDays, byBand, new Concentration(topMinutes, totalMinutes - topMinutes),
				new Recurrence(recurrentMinutes, totalMinutes - recurrentMinutes), everyone);
	}

	/**
	 * Las ventanas confirmadas con tiempo dentro de {@code [from, to)}, por inicio, cada una con su porcion:
	 * lo que suma el detalle de un informe es igual al tiempo oficial.
	 */
	public List<WindowPortion> confirmedWindows(Collection<IntermittencyWindow> windows, Instant from, Instant to,
			Instant cutOff) {
		List<WindowPortion> portions = new ArrayList<>();
		for (IntermittencyWindow window : windows) {
			if (window.getState() != WindowState.CONFIRMED) {
				continue;
			}
			long minutes = slicer.minutesWithin(window.getStartAt(), window.getEndAt(), from, to, cutOff);
			if (minutes > 0) {
				portions.add(new WindowPortion(window, minutes));
			}
		}
		portions.sort(Comparator.comparing((WindowPortion portion) -> portion.window().getStartAt())
				.thenComparing(portion -> portion.window().getId()));
		return portions;
	}

	/**
	 * Todos los puntos de mayor a menor tiempo. Entre iguales: mas ventanas, mas dias y nombre actual sin
	 * distinguir mayusculas ni tildes. Ese orden no excluye a nadie.
	 */
	private static List<TopPoint> ranked(Collection<PointTotals> points, Map<UUID, PointLabel> labels) {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("es-EC"));
		collator.setStrength(Collator.PRIMARY);
		List<TopPoint> ranked = new ArrayList<>();
		for (PointTotals point : points) {
			PointLabel label = labels.get(point.equipmentId);
			ranked.add(new TopPoint(point.equipmentId, label == null ? null : label.code(),
					label == null ? point.fallbackName : label.name(), point.minutes, point.windows,
					point.days.size()));
		}
		ranked.sort(Comparator.comparingLong(TopPoint::minutes).reversed()
				.thenComparing(Comparator.comparingInt(TopPoint::windows).reversed())
				.thenComparing(Comparator.comparingInt(TopPoint::days).reversed())
				.thenComparing(TopPoint::name, collator).thenComparing(TopPoint::equipmentId));
		return List.copyOf(ranked);
	}

	/** Los diez primeros con todos los empatados con el decimo puesto. */
	private static List<TopPoint> topOf(List<TopPoint> everyone) {
		if (everyone.size() <= TOP_SIZE) {
			return everyone;
		}
		long threshold = everyone.get(TOP_SIZE - 1).minutes();
		return everyone.stream().filter(point -> point.minutes() >= threshold).toList();
	}

	/** Todos los dias del periodo hasta el dia del corte, en cero los que no tienen tiempo confirmado. */
	private List<DailyPoint> dailySeries(Map<LocalDate, long[]> daily, Instant from, Instant to, Instant cutOff) {
		LocalDate cutOffDay = slicer.dayOf(cutOff);
		List<DailyPoint> series = new ArrayList<>();
		for (LocalDate day = slicer.dayOf(from); slicer.startOf(day).isBefore(to)
				&& !day.isAfter(cutOffDay); day = day.plusDays(1)) {
			long[] totals = daily.getOrDefault(day, new long[2]);
			series.add(new DailyPoint(day, totals[0], (int) totals[1]));
		}
		return series;
	}
}
