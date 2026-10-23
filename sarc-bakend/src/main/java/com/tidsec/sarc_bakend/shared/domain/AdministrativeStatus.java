package com.tidsec.sarc_bakend.shared.domain;

import java.util.Arrays;

/**
 * Estado administrativo de un registro maestro (PRD 6.1: 1=ACTIVO, 2=INACTIVO, 0=ELIMINADO).
 * ELIMINADO es una baja logica: el registro y sus vinculos historicos se conservan y puede
 * recuperarse. Es independiente de cualquier estado operativo del registro.
 */
public enum AdministrativeStatus {

	DELETED(0),
	ACTIVE(1),
	INACTIVE(2);

	private final int code;

	AdministrativeStatus(int code) {
		this.code = code;
	}

	public int code() {
		return code;
	}

	public static AdministrativeStatus fromCode(int code) {
		return Arrays.stream(values()).filter(status -> status.code == code).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Estado administrativo desconocido: " + code));
	}
}
