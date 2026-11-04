package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

/**
 * De donde sale el uptime de una medicion. {@code STATES}: de los cuatro estados (FR-006).
 * {@code REPORTED_PERCENT}: del porcentaje de la fuente, cuando el informe semanal no trae Ocupado; el
 * tiempo evaluado es la duracion del periodo.
 */
public enum UptimeBasis {

	STATES,
	REPORTED_PERCENT
}
