package com.tidsec.sarc_bakend.modules.uptime.application.service;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/** Lectura operativa de uptime. No concede permisos de escritura. */
@Component
class UptimeReadAccess {

	private final CurrentActor actor;

	UptimeReadAccess(CurrentActor actor) {
		this.actor = actor;
	}

	boolean canReadOperational() {
		return actor.hasAnyRole("ADMINISTRADOR", "ANALISTA");
	}
}
