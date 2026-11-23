package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;

/**
 * Los hitos de un episodio tal como se conocian en un instante (FR-074a, FR-086, SC-012). Las revisiones solo se anexan:
 * cada una guarda el valor anterior y el nuevo con el momento en que se registro, asi que aplicar las de
 * {@code recordedAt <= X} reproduce lo que el sistema sabia en X, y una correccion posterior no lo cambia. Con ello un
 * informe ya emitido se puede reconstruir y comparar con su huella. Funcion pura.
 */
public final class MilestonesAsKnownAt {

	/** El estado y la hora de un hito en el instante consultado; la hora es nula salvo en {@code RECORDED}. */
	public record Value(MilestoneStatus status, Instant occurredAt) {
	}

	private MilestonesAsKnownAt() {
	}

	/**
	 * @param revisions las revisiones del episodio, en cualquier orden; las de otro episodio se ignoran
	 * @param instant el corte: solo cuentan las revisiones registradas hasta ese instante, inclusive
	 */
	public static Map<MilestoneKind, Value> of(Episode episode, List<MilestoneRevision> revisions, Instant instant) {
		Map<MilestoneKind, Value> known = new EnumMap<>(MilestoneKind.class);
		episode.milestonesByKind().forEach((kind, current) -> {
			List<MilestoneRevision> ofKind = revisions.stream()
					.filter(revision -> revision.getEpisodeId().equals(episode.getId()) && revision.getKind() == kind)
					.sorted(Comparator.comparing(MilestoneRevision::getRecordedAt)).toList();
			known.put(kind, valueOf(ofKind, instant, new Value(current.getStatus(), current.getOccurredAt())));
		});
		return known;
	}

	private static Value valueOf(List<MilestoneRevision> ofKind, Instant instant, Value current) {
		if (ofKind.isEmpty()) {
			return current;
		}
		MilestoneRevision applied = null;
		for (MilestoneRevision revision : ofKind) {
			if (!revision.getRecordedAt().isAfter(instant)) {
				applied = revision;
			}
		}
		if (applied != null) {
			return new Value(applied.getNewStatus(), applied.getNewOccurredAt());
		}
		// Antes de la primera revision: el valor original, que esa revision guardo como anterior.
		MilestoneRevision first = ofKind.getFirst();
		MilestoneStatus status = first.getPreviousStatus() == null ? MilestoneStatus.PENDING : first.getPreviousStatus();
		return new Value(status, first.getPreviousOccurredAt());
	}
}
