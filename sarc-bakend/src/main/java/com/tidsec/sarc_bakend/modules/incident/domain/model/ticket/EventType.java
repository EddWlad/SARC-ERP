package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;

/**
 * Tipos de evento de la cronologia (data-model.md). Un evento nunca se borra: solo se anula con motivo.
 */
public enum EventType {

	TICKET_CREATED,
	VALIDATION,
	CORRECTIVE_ACTION,
	ESCALATED,
	OBSERVATION,
	DATA_CORRECTED,
	PRIORITY_CHANGED,
	RESPONSIBLE_ASSIGNED,
	SHIFT_HANDOVER,
	RECLASSIFIED,
	VISIT_STAGE,
	COMMUNICATION_RECORDED,
	EVIDENCE_ADDED,
	MILESTONE_RECORDED,
	MILESTONE_CORRECTED,
	TICKET_CLOSED,
	TICKET_REOPENED,
	RELATION_ADDED;

	/**
	 * Los cuatro tipos que el analista registra con {@code POST /events}; los demas los genera el sistema como efecto
	 * de otra operacion.
	 */
	public boolean isManual() {
		return this == VALIDATION || this == CORRECTIVE_ACTION || this == ESCALATED || this == OBSERVATION;
	}

	/**
	 * Los eventos que se pueden anular con motivo: los manuales, la carga de una evidencia y el registro de una
	 * comunicacion, que no se borran (FR-036, FR-037).
	 * Los demas son consecuencia de otra operacion y se corrigen con ella.
	 */
	public boolean isAnnullable() {
		return isManual() || this == EVIDENCE_ADDED || this == COMMUNICATION_RECORDED;
	}

	/**
	 * Si el evento alimentara la campana futura (research R17, FR-096): ticket nuevo, cierre, reapertura,
	 * escalamiento y paso a prioridad Critica. {@code newPriority} solo importa en un cambio de prioridad.
	 */
	public boolean isNotifiable(Priority newPriority) {
		return switch (this) {
			case TICKET_CREATED, TICKET_CLOSED, TICKET_REOPENED, ESCALATED -> true;
			case PRIORITY_CHANGED -> newPriority == Priority.CRITICAL;
			default -> false;
		};
	}
}
