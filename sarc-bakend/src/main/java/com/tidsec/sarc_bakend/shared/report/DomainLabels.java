package com.tidsec.sarc_bakend.shared.report;

import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/** Textos en espanol del vocabulario comun ({@code shared/domain}) para los reportes de cualquier modulo. */
public final class DomainLabels {

	private DomainLabels() {
	}

	/** Vacio si no hay tipo (p. ej. una sesion aun no vinculada a un equipo). */
	public static String of(InfrastructureType type) {
		if (type == null) {
			return "";
		}
		return switch (type) {
			case CHARGING_POINT -> "Punto de carga";
			case CHARGING_STATION -> "Electrolinera";
		};
	}
}
