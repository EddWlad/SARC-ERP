package com.tidsec.sarc_bakend.modules.incident.application.result;

/**
 * Estado de la numeracion para la pantalla de configuracion. {@code nextCode} es informativo: el numero se asigna
 * solo al crear. {@code expectedEnvironment} es nulo si la propiedad del servidor falta; en ese caso no se puede
 * configurar ni crear.
 */
public record NumberingView(boolean configured, String environment, Integer startNumber, String nextCode,
		String expectedEnvironment, boolean ticketsExist) {
}
