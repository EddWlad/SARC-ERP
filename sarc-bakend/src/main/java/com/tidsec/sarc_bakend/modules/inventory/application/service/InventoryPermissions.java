package com.tidsec.sarc_bakend.modules.inventory.application.service;

/**
 * Permisos del inventario (PRD seccion 4): todos los roles consultan; administradores y analistas
 * editan y son los unicos que ven UID/PUK y URL OCPP.
 */
public final class InventoryPermissions {

	/** Expresion para {@code @PreAuthorize}. */
	public static final String EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')";

	static final String[] RESTRICTED_DATA_VIEWERS = { "ADMINISTRADOR", "ANALISTA" };

	private InventoryPermissions() {
	}
}
