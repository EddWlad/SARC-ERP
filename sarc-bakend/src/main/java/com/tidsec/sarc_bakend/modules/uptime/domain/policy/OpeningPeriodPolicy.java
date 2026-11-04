package com.tidsec.sarc_bakend.modules.uptime.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.PeriodType;

/** Regla de apertura (FR-030, FR-032, DP-01). Aislada para poder cambiarla sin tocar nada mas. */
public final class OpeningPeriodPolicy {

	public enum Kind {
		OFFICIAL,
		PROVISIONAL,
		NONE
	}

	/** Ventana con mediciones incluidas. */
	public record Window(PeriodType type, Instant start, Instant end) {
	}

	public record Opening(Kind kind, PeriodType type, Instant from, Instant to) {
	}

	/** Fines en {@code (after, until]}. */
	public record EndRange(Instant after, Instant until) {

		public boolean includes(Instant end) {
			return end.isAfter(after) && !end.isAfter(until);
		}
	}

	private OpeningPeriodPolicy() {
	}

	public static Opening resolve(Collection<Window> windows) {
		Optional<Window> week = windows.stream().filter(window -> window.type().isExact())
				.max(Comparator.comparing(Window::end));
		if (week.isPresent()) {
			return new Opening(Kind.OFFICIAL, PeriodType.EXACT_WEEK, week.get().start(), week.get().end());
		}
		return preferredProvisional(windows).map(type -> {
			List<Window> ofType = ofType(windows, type);
			EndRange range = range(type, ofType);
			List<Window> shown = ofType.stream().filter(window -> range.includes(window.end())).toList();
			return new Opening(Kind.PROVISIONAL, type,
					shown.stream().map(Window::start).min(Comparator.naturalOrder()).orElseThrow(), range.until());
		}).orElse(new Opening(Kind.NONE, null, null, null));
	}

	/** 7 dias; si no hay, la mas reciente y, a igual fin, la de menor duracion. */
	public static Optional<PeriodType> preferredProvisional(Collection<Window> windows) {
		List<Window> rolling = windows.stream().filter(window -> !window.type().isExact()).toList();
		if (rolling.stream().anyMatch(window -> window.type() == PeriodType.ROLLING_7D)) {
			return Optional.of(PeriodType.ROLLING_7D);
		}
		return rolling.stream()
				.max(Comparator.comparing(Window::end).thenComparing(OpeningPeriodPolicy::duration, Comparator.reverseOrder()))
				.map(Window::type);
	}

	/** Sin rango elegido entran los fines dentro de una duracion nominal antes de la mas reciente. */
	public static EndRange range(PeriodType type, Collection<Window> windows) {
		Instant latest = ofType(windows, type).stream().map(Window::end).max(Comparator.naturalOrder()).orElseThrow();
		return new EndRange(latest.minus(type.nominal()), latest);
	}

	private static List<Window> ofType(Collection<Window> windows, PeriodType type) {
		return windows.stream().filter(window -> window.type() == type).toList();
	}

	private static Duration duration(Window window) {
		return Duration.between(window.start(), window.end());
	}
}
