package com.tidsec.sarc_bakend.modules.intermittency.domain.model.coverage;

/** Lo que paso con la marca de un dia revisado. */
public enum ReviewedDayAction {
	/** Un Analista o Administrador marco el dia como revisado para toda la red KIA (FR-044). */
	MARKED,
	/** Un editor retiro la marca, con justificacion. */
	WITHDRAWN
}
