package com.tidsec.sarc_bakend.shared.security;

import org.springframework.stereotype.Component;

/** Lectura operativa común a consumo y archivo. No concede permisos de escritura. */
@Component
public class ConsumptionReadAccess {

	private final CurrentActor actor;

	public ConsumptionReadAccess(CurrentActor actor) {
		this.actor = actor;
	}

	public boolean canReadOperational() {
		return actor.hasAnyRole("ADMINISTRADOR", "ANALISTA");
	}
}
