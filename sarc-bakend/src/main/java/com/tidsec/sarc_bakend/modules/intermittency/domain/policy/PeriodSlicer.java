package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Reparte una ventana {@code [inicio, fin)} entre los dias locales y los periodos (FR-013): a cada uno le
 * toca solo el tiempo que cae dentro, sin duplicar el evento. Una ventana abierta llega hasta
 * {@code min(corte, hasta)}. Todos los limites son minutos exactos, asi que las duraciones son enteras.
 */
public final class PeriodSlicer {

	/** Minutos de una ventana que caen en un dia local. */
	public record DaySlice(LocalDate day, long minutes) {
	}

	private final ZoneId zone;

	public PeriodSlicer(ZoneId zone) {
		this.zone = zone;
	}

	/** Minutos de la ventana dentro de {@code [from, to)}; cero si no se intersecan. */
	public long minutesWithin(Instant start, Instant end, Instant from, Instant to, Instant cutOff) {
		Instant effectiveEnd = effectiveEnd(end, to, cutOff);
		Instant lower = start.isAfter(from) ? start : from;
		Instant upper = effectiveEnd.isBefore(to) ? effectiveEnd : to;
		return upper.isAfter(lower) ? Duration.between(lower, upper).toMinutes() : 0;
	}

	/** Reparto por dia local de la parte de la ventana que cae en {@code [from, to)}, solo dias con tiempo. */
	public List<DaySlice> byDay(Instant start, Instant end, Instant from, Instant to, Instant cutOff) {
		Instant effectiveEnd = effectiveEnd(end, to, cutOff);
		Instant cursor = start.isAfter(from) ? start : from;
		Instant limit = effectiveEnd.isBefore(to) ? effectiveEnd : to;
		List<DaySlice> slices = new ArrayList<>();
		while (cursor.isBefore(limit)) {
			LocalDate day = cursor.atZone(zone).toLocalDate();
			Instant nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant();
			Instant sliceEnd = nextMidnight.isBefore(limit) ? nextMidnight : limit;
			slices.add(new DaySlice(day, Duration.between(cursor, sliceEnd).toMinutes()));
			cursor = sliceEnd;
		}
		return slices;
	}

	public LocalDate dayOf(Instant instant) {
		return instant.atZone(zone).toLocalDate();
	}

	public Instant startOf(LocalDate day) {
		return day.atStartOfDay(zone).toInstant();
	}

	private static Instant effectiveEnd(Instant end, Instant to, Instant cutOff) {
		if (end != null) {
			return end;
		}
		return cutOff.isBefore(to) ? cutOff : to;
	}
}
