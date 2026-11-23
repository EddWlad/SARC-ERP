package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Milestone;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;

/**
 * Duraciones de un episodio (FR-045, FR-046): primera validacion (T1−T0), primera accion (T2−T0), recuperacion
 * (T3−T0), cierre (T4−T0) y recuperacion a cierre (T4−T3), en minutos enteros redondeados hacia abajo. Cada una se
 * calcula solo si ambos hitos tienen hora; si falta alguno es {@code null}, nunca 0 (FR-042). El tiempo transcurrido de un
 * episodio abierto se informa aparte y no entra en los promedios de hitos que todavia no existen. Funciones puras.
 */
public final class EpisodeDurations {

	/** Un par de hitos, su duracion en minutos (nula si no es calculable) y los hitos que faltan. */
	public record Pair(String pair, Integer minutes, List<MilestoneKind> missing) {
	}

	private static final MilestoneKind[][] PAIRS = { { MilestoneKind.T1, MilestoneKind.T0 },
			{ MilestoneKind.T2, MilestoneKind.T0 }, { MilestoneKind.T3, MilestoneKind.T0 },
			{ MilestoneKind.T4, MilestoneKind.T0 }, { MilestoneKind.T4, MilestoneKind.T3 } };

	private EpisodeDurations() {
	}

	/** Los cinco pares del episodio, siempre en el mismo orden. */
	public static List<Pair> pairs(Episode episode) {
		List<Pair> result = new ArrayList<>();
		for (MilestoneKind[] pair : PAIRS) {
			Instant end = timeOf(episode, pair[0]);
			Instant start = timeOf(episode, pair[1]);
			List<MilestoneKind> missing = new ArrayList<>();
			if (end == null) {
				missing.add(pair[0]);
			}
			if (start == null) {
				missing.add(pair[1]);
			}
			Integer minutes = missing.isEmpty() ? minutesBetween(start, end) : null;
			result.add(new Pair(pair[0] + "-" + pair[1], minutes, missing));
		}
		return result;
	}

	/** Minutos enteros (hacia abajo) entre los dos hitos; nulo si falta alguno. */
	public static Integer minutes(Episode episode, MilestoneKind end, MilestoneKind start) {
		Instant to = timeOf(episode, end);
		Instant from = timeOf(episode, start);
		return to == null || from == null ? null : minutesBetween(from, to);
	}

	/** Segundos exactos entre los dos hitos; nulo si falta alguno. Las comparaciones con una referencia usan este valor. */
	public static Long seconds(Episode episode, MilestoneKind end, MilestoneKind start) {
		Instant to = timeOf(episode, end);
		Instant from = timeOf(episode, start);
		return to == null || from == null ? null : Duration.between(from, to).getSeconds();
	}

	/** Minutos transcurridos desde T0 hasta el corte en un episodio abierto; nulo si esta cerrado. */
	public static Long elapsedOpenMinutes(Episode episode, Instant cutOff) {
		Instant t0 = timeOf(episode, MilestoneKind.T0);
		if (!episode.isOpen() || t0 == null) {
			return null;
		}
		return Math.max(0, Duration.between(t0, cutOff).toMinutes());
	}

	/** La hora de un hito si tiene (registrado); nula si esta pendiente, «No aplica» o «Sin dato». */
	public static Instant timeOf(Episode episode, MilestoneKind kind) {
		Milestone milestone = episode.milestone(kind);
		return milestone.getStatus() == MilestoneStatus.RECORDED ? milestone.getOccurredAt() : null;
	}

	private static int minutesBetween(Instant from, Instant to) {
		return (int) Math.floorDiv(Duration.between(from, to).getSeconds(), 60);
	}
}
