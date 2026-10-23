package com.tidsec.sarc_bakend.modules.consumption.domain.model.session;

import com.tidsec.sarc_bakend.shared.domain.ExternalPlatform;

/**
 * Plataforma que produjo la sesion. Es la procedencia del dato, independiente de la marca fisica
 * del equipo (PRD RF-003.12: un equipo Wallbox puede reportar por Enerlink).
 */
public enum SessionSource {

	ENERLINK(ExternalPlatform.ENERLINK),
	WALLBOX(ExternalPlatform.WALLBOX);

	private final ExternalPlatform platform;

	SessionSource(ExternalPlatform platform) {
		this.platform = platform;
	}

	/** Plataforma con la que se buscan sus identidades externas en el inventario. */
	public ExternalPlatform platform() {
		return platform;
	}
}
