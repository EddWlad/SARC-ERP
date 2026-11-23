package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.Optional;

/**
 * Condicion de un ticket abierto en el tablero (FR-062). No es un estado: se deriva de los hitos del episodio vigente
 * para no agregar estados principales (FR-030).
 *
 * <ul>
 * <li>{@link #RECOVERED_OBSERVING}: T3 registrado (recuperado, en observacion).
 * <li>{@link #IN_ATTENTION}: T2 registrado y T3 pendiente.
 * <li>{@link #PENDING_VALIDATION}: T1 pendiente (sin validar).
 * </ul>
 *
 * Si un ticket cumple varias, la primera de esta lista es la que se muestra.
 */
public enum TicketCondition {

	RECOVERED_OBSERVING,
	IN_ATTENTION,
	PENDING_VALIDATION;

	/** La condicion de un episodio abierto; vacia si el ticket esta cerrado o ya valido y aun no actuo. */
	public static Optional<TicketCondition> of(Episode episode) {
		if (!episode.isOpen()) {
			return Optional.empty();
		}
		MilestoneStatus t1 = episode.milestone(MilestoneKind.T1).getStatus();
		MilestoneStatus t2 = episode.milestone(MilestoneKind.T2).getStatus();
		MilestoneStatus t3 = episode.milestone(MilestoneKind.T3).getStatus();
		if (t3 == MilestoneStatus.RECORDED) {
			return Optional.of(RECOVERED_OBSERVING);
		}
		if (t2 == MilestoneStatus.RECORDED && t3 == MilestoneStatus.PENDING) {
			return Optional.of(IN_ATTENTION);
		}
		if (t1 == MilestoneStatus.PENDING) {
			return Optional.of(PENDING_VALIDATION);
		}
		return Optional.empty();
	}
}
