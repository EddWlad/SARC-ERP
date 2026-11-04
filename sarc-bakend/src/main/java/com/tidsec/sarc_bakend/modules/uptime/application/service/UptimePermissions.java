package com.tidsec.sarc_bakend.modules.uptime.application.service;

/** Expresiones de autorizacion del modulo de uptime. */
final class UptimePermissions {

	static final String EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	private UptimePermissions() {
	}
}
