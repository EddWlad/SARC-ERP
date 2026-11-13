package com.tidsec.sarc_bakend.modules.intermittency.application.service;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Que puede ver quien consulta, con los roles vigentes de cada peticion (FR-007): un usuario con varios roles
 * recibe la union de sus permisos. No concede permisos de escritura.
 */
@Component
class IntermittencyReadAccess {

	private final CurrentActor actor;

	IntermittencyReadAccess(CurrentActor actor) {
		this.actor = actor;
	}

	/** Detalle operativo: ventanas por revisar, abiertas, de prueba, procedencia e historial (FR-005). */
	boolean canReadOperational() {
		return actor.hasAnyRole("ADMINISTRADOR", "ANALISTA");
	}

	/** Disponibilidad estimada, restringida a Analistas durante la prueba (FR-006). */
	boolean canReadAvailability() {
		return actor.hasAnyRole("ADMINISTRADOR", "ANALISTA");
	}
}
