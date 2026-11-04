package com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement;

/** Que representa una lectura. Solo {@code MEASURED} lleva minutos y entra al promedio. */
public enum ReadingClass {

	MEASURED,
	NO_DATA,
	OUT_OF_SCOPE,
	RETIRED
}
