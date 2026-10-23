package com.tidsec.sarc_bakend.modules.inventory.domain.model;

/** Estado de uso de un punto de carga: publico, solo autorizados o instalado sin servicio. */
public enum UsageStatus {
	FREE,
	BLOCKED,
	DISCONNECTED
}
