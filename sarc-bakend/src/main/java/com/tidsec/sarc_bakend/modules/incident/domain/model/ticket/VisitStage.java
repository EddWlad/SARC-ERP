package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import java.util.Set;

/**
 * Etapas de una visita tecnica registrada a mano (P-08). La visita no es un estado principal del ticket: avanza por
 * estas etapas y cada cambio deja un evento {@code VISIT_STAGE}. Camino normal: solicitada, asignada, confirmada,
 * ejecutada, informe recibido. Antes de ejecutarse se puede cancelar o aplazar; una visita aplazada vuelve a asignarse
 * o a confirmarse.
 */
public enum VisitStage {

	REQUESTED,
	ASSIGNED,
	CONFIRMED,
	CANCELLED,
	POSTPONED,
	EXECUTED,
	REPORT_RECEIVED;

	/** Etapas a las que se puede pasar desde esta. Las finales ({@code CANCELLED}, {@code REPORT_RECEIVED}) no tienen. */
	public Set<VisitStage> next() {
		return switch (this) {
			case REQUESTED -> Set.of(ASSIGNED, CANCELLED, POSTPONED);
			case ASSIGNED -> Set.of(CONFIRMED, CANCELLED, POSTPONED);
			case CONFIRMED -> Set.of(EXECUTED, CANCELLED, POSTPONED);
			case POSTPONED -> Set.of(ASSIGNED, CONFIRMED);
			case EXECUTED -> Set.of(REPORT_RECEIVED);
			case CANCELLED, REPORT_RECEIVED -> Set.of();
		};
	}

	public boolean canMoveTo(VisitStage target) {
		return next().contains(target);
	}

	/** Cancelar o aplazar exige quien decidio, la evidencia y la condicion de nueva escalada (Protocolo 9.4, FR-035). */
	public boolean needsDecision() {
		return this == CANCELLED || this == POSTPONED;
	}
}
