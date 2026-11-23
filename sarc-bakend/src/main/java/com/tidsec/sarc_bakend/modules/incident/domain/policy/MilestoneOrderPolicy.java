package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Instant;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;

/**
 * Reglas de orden de los hitos de un episodio (FR-043): ningun hito es anterior a T0 ni posterior al momento del
 * registro; T4 no es anterior a ningun hito registrado; y si T1, T2 y T3 no siguen ese orden se admiten solo con la
 * confirmacion del analista, que queda guardada. Funciones puras, sin Spring.
 */
public final class MilestoneOrderPolicy {

	private MilestoneOrderPolicy() {
	}

	/**
	 * Valida una hora candidata para {@code kind}.
	 *
	 * @param others las horas registradas de los demas hitos del episodio (sin el que se esta registrando)
	 * @param orderAck el analista confirmo que T1, T2 y T3 no siguen el orden
	 * @return {@code true} si el orden T1 ≤ T2 ≤ T3 no se cumple y se admitio por la confirmacion
	 */
	public static boolean check(MilestoneKind kind, Instant candidate, Instant now, Map<MilestoneKind, Instant> others,
			boolean orderAck) {
		Instant t0 = kind == MilestoneKind.T0 ? candidate : others.get(MilestoneKind.T0);
		if (candidate.isAfter(now)) {
			throw IncidentErrors.milestoneInFuture();
		}
		if (kind != MilestoneKind.T0 && t0 != null && candidate.isBefore(t0)) {
			throw IncidentErrors.milestoneBeforeT0();
		}
		if (kind == MilestoneKind.T0) {
			// Corregir T0 no puede dejar un hito registrado antes de el.
			boolean someBefore = others.entrySet().stream().filter(e -> e.getKey() != MilestoneKind.T0)
					.anyMatch(e -> e.getValue().isBefore(candidate));
			if (someBefore) {
				throw IncidentErrors.milestoneInvalid("T0 no puede ser posterior a un hito ya registrado");
			}
		}
		if (kind == MilestoneKind.T4) {
			boolean someAfter = others.entrySet().stream().filter(e -> e.getKey() != MilestoneKind.T4)
					.anyMatch(e -> e.getValue().isAfter(candidate));
			if (someAfter) {
				throw IncidentErrors.milestoneT4BeforeMilestones();
			}
			return false;
		}
		boolean outOfOrder = outOfOrder(kind, candidate, others);
		if (outOfOrder && !orderAck) {
			throw IncidentErrors.milestoneOrderUnconfirmed();
		}
		return outOfOrder;
	}

	/** Con la hora candidata, algun par de T1, T2 y T3 registrados queda en desorden. */
	private static boolean outOfOrder(MilestoneKind kind, Instant candidate, Map<MilestoneKind, Instant> others) {
		if (kind != MilestoneKind.T1 && kind != MilestoneKind.T2 && kind != MilestoneKind.T3) {
			return false;
		}
		Instant t1 = kind == MilestoneKind.T1 ? candidate : others.get(MilestoneKind.T1);
		Instant t2 = kind == MilestoneKind.T2 ? candidate : others.get(MilestoneKind.T2);
		Instant t3 = kind == MilestoneKind.T3 ? candidate : others.get(MilestoneKind.T3);
		return isAfter(t1, t2) || isAfter(t2, t3) || isAfter(t1, t3);
	}

	private static boolean isAfter(Instant first, Instant second) {
		return first != null && second != null && first.isAfter(second);
	}
}
