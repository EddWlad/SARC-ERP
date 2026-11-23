package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;

/**
 * Juicio de un episodio frente a las referencias de tiempo de su version (FR-051 a FR-054, P-03, P-06). Son referencias
 * operativas, no SLA. La atencion referencial se compara con la recuperacion (T3−T0), nunca con el cierre; terminar
 * antes del limite inferior no es desviacion. Un episodio sin el hito necesario es «no evaluable», nunca «dentro». Los
 * tipos sin referencia aprobada no producen juicio. Funciones puras, sin Spring.
 */
public final class ReferenceEvaluator {

	public enum Measure {
		VALIDATION, RECOVERY, ESCALATION
	}

	public enum Result {
		WITHIN, OUTSIDE, NOT_EVALUABLE, NO_REFERENCE
	}

	/** El juicio de una medida: su limite en minutos, si es ampliable con justificacion, y el resultado. */
	public record Judgment(Measure measure, int limitMinutes, boolean extendable, Result result) {
	}

	private ReferenceEvaluator() {
	}

	/**
	 * Los juicios del episodio, uno por cada medida con limite en la regla. Vacia si el tipo no tiene regla.
	 *
	 * @param events los eventos del ticket; solo cuentan los del episodio, no anulados
	 * @param cutOff el momento del corte, para medir un episodio abierto
	 */
	public static List<Judgment> evaluate(Episode episode, Optional<ReferenceRule> rule, List<TicketEvent> events,
			Instant cutOff) {
		List<Judgment> judgments = new ArrayList<>();
		if (rule.isEmpty()) {
			return judgments;
		}
		ReferenceRule reference = rule.get();
		reference.validationMaxMin().ifPresent(limit -> judgments.add(new Judgment(Measure.VALIDATION, limit, false,
				exceeds(EpisodeDurations.seconds(episode, MilestoneKind.T1, MilestoneKind.T0), limit))));
		reference.recoveryMaxMin().ifPresent(limit -> judgments.add(new Judgment(Measure.RECOVERY, limit,
				reference.isRecoveryExtendable(),
				exceeds(EpisodeDurations.seconds(episode, MilestoneKind.T3, MilestoneKind.T0), limit))));
		reference.escalationMaxMin().ifPresent(limit -> judgments
				.add(new Judgment(Measure.ESCALATION, limit, false, escalation(episode, limit, events, cutOff))));
		return judgments;
	}

	/** Una duracion ausente no es evaluable; si supera el limite, esta fuera; si no, dentro. */
	private static Result exceeds(Long seconds, int limitMinutes) {
		if (seconds == null) {
			return Result.NOT_EVALUABLE;
		}
		return seconds > limitMinutes * 60L ? Result.OUTSIDE : Result.WITHIN;
	}

	/**
	 * Escalamiento de la Espera de conexion (FR-052). {@code waitEnd} es T3 registrado o, con el episodio abierto, el
	 * corte. Solo cuenta el primer escalamiento no anulado con destino tecnico hecho antes del fin de la espera; uno
	 * posterior no cuenta. Si ese escalamiento llego dentro del limite, esta dentro; si no, se compara la espera con el
	 * limite. Cerrado sin T3 ni escalamiento tecnico no es evaluable.
	 */
	static Result escalation(Episode episode, int limitMinutes, List<TicketEvent> events, Instant cutOff) {
		Instant t0 = EpisodeDurations.timeOf(episode, MilestoneKind.T0);
		Instant t3 = EpisodeDurations.timeOf(episode, MilestoneKind.T3);
		Instant waitEnd = t3 != null ? t3 : episode.isOpen() ? cutOff : null;
		Optional<Instant> escalation = events.stream()
				.filter(event -> episode.getId().equals(event.getEpisodeId()))
				.filter(event -> event.getType() == EventType.ESCALATED && !event.isAnnulled()
						&& event.getTargetKind() == TargetKind.TECHNICAL)
				.map(TicketEvent::getOccurredAt).filter(at -> waitEnd == null || !at.isAfter(waitEnd))
				.min(Comparator.naturalOrder());
		long limitSeconds = limitMinutes * 60L;
		if (escalation.isPresent()) {
			boolean inTime = java.time.Duration.between(t0, escalation.get()).getSeconds() <= limitSeconds;
			return inTime ? Result.WITHIN : Result.OUTSIDE;
		}
		if (waitEnd == null) {
			return Result.NOT_EVALUABLE;
		}
		return java.time.Duration.between(t0, waitEnd).getSeconds() > limitSeconds ? Result.OUTSIDE : Result.WITHIN;
	}

	/**
	 * Un punto KIA con la caida de {@code exactSeconds} es persistente solo si supera estrictamente el umbral (P-04):
	 * 30 min exactos aun no lo es, 30 min 1 s si. La duracion no se redondea a minutos.
	 */
	public static boolean isPersistentOutage(long exactSeconds, ReferenceRule rule) {
		return rule.persistentAfterMin().map(minutes -> exactSeconds > minutes * 60L).orElse(false);
	}
}
