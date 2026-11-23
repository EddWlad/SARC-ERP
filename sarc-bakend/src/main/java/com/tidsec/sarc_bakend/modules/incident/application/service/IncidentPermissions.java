package com.tidsec.sarc_bakend.modules.incident.application.service;

/**
 * Expresiones de autorizacion del modulo de incidencias (feature 010, contracts/incidents-api.md), con el mismo
 * patron que {@code IntermittencyPermissions}. Viven en {@code application.service} porque los casos de uso las
 * declaran con {@code @PreAuthorize} y ArchUnit prohibe que {@code application} dependa de {@code infrastructure}.
 * Se evaluan en cada operacion: un usuario con varios roles recibe la union de sus permisos.
 */
final class IncidentPermissions {

	/** Crear, dar seguimiento, corregir, cerrar y reabrir. */
	static final String EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	/** Consultar tickets, tablero y reportes: los cinco roles. */
	static final String READERS = "hasAnyRole('ADMINISTRADOR','ANALISTA','GERENTE','SUPERVISOR','ASISTENTE')";

	/** Configurar el numero inicial de tickets. */
	static final String NUMBERING = "hasRole('ADMINISTRADOR')";

	private IncidentPermissions() {
	}
}
