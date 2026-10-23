package com.tidsec.sarc_bakend.shared.error;

import java.util.Map;

/** Un servicio externo necesario para la operacion (almacenamiento, correo...) no esta disponible. */
public final class ServiceUnavailableException extends SarcException {

	public ServiceUnavailableException(String code, String message) {
		super(code, message, Map.of());
	}
}
