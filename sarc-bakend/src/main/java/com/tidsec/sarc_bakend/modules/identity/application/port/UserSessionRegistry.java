package com.tidsec.sarc_bakend.modules.identity.application.port;

import java.util.UUID;

/** Puerto de salida: revoca las sesiones vigentes de un usuario cuando cambian sus credenciales o permisos. */
public interface UserSessionRegistry {

	void invalidateAll(UUID userId);
}
