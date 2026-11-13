package com.tidsec.sarc_bakend.modules.reporting.domain.model;

/**
 * Tipos de reporte emitido cuyo contenido operativo se restringe segun el permiso vigente de quien lo pide
 * (Feature 004): una copia con operaciones no se entrega a un rol de consulta, aunque la haya emitido otro.
 */
public final class IssuedReportKinds {

	private IssuedReportKinds() {
	}

	public static boolean restrictable(String kind) {
		return "CONSUMPTION".equals(kind) || "INTERMITTENCY".equals(kind);
	}
}
