package com.tidsec.sarc_bakend.modules.consumption.application.service;

/**
 * Permisos de consumo (PRD seccion 4): todos los roles consultan e informan; administradores y
 * analistas importan, concilian y corrigen.
 */
public final class ConsumptionPermissions {

	/** Expresion para {@code @PreAuthorize}. */
	public static final String EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	private ConsumptionPermissions() {
	}
}
