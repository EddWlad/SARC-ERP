package com.tidsec.sarc_bakend.modules.intermittency.application.service;

/**
 * Expresiones de autorizacion del modulo de intermitencias. {@code AVAILABILITY} es una constante aparte de
 * {@code EDITORS} aunque hoy tenga la misma expresion: la visibilidad de la disponibilidad estimada es una
 * excepcion de la etapa de prueba (FR-006) y debe poder cambiar sin tocar quien edita.
 */
final class IntermittencyPermissions {

	static final String EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	static final String AVAILABILITY = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	private IntermittencyPermissions() {
	}
}
