package com.tidsec.sarc_bakend.modules.identity.application.port;

/** Puerto de salida: cuenta los ingresos fallidos por origen (direccion del cliente). */
public interface LoginThrottle {

	/**
	 * Rechaza con {@code TooManyRequestsException} si el origen agoto sus intentos en la ventana
	 * vigente.
	 */
	void requireAllowed(String client);

	void registerFailure(String client);
}
