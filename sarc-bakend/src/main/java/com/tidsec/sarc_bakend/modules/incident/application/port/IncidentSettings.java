package com.tidsec.sarc_bakend.modules.incident.application.port;

import java.util.Optional;

/**
 * Ajustes de configuracion que necesitan los casos de uso de incidencias. Es un puerto porque {@code application}
 * no puede conocer {@code infrastructure.config}; lo implementa {@code IncidentSettingsAdapter} con
 * {@code IncidentProperties}.
 */
public interface IncidentSettings {

	/**
	 * Ambiente de numeracion del servidor ({@code PRODUCTION} o {@code TEST}). Vacio si la propiedad
	 * {@code sarc.incidents.numbering.environment} falta: la aplicacion arranca, pero no se configura la numeracion
	 * ni se crean tickets (research R2).
	 */
	Optional<String> expectedNumberingEnvironment();

	/** Tamano maximo de una evidencia, en bytes. */
	long evidenceMaxBytes();
}
