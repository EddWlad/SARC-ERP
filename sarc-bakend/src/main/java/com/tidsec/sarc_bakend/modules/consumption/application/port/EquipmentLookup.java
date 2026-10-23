package com.tidsec.sarc_bakend.modules.consumption.application.port;

import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;

/** Como reporta cada fuente al equipo: por serie fisica (Wallbox) o por identidad externa (Enerlink). */
public sealed interface EquipmentLookup {

	/** Serie del cargador tal como la reporta la plataforma. */
	record BySerial(String serial) implements EquipmentLookup {
	}

	/** Ambito y nombre con que la plataforma identifica al cargador (Enerlink: ACTIVO y CARGADOR). */
	record ByIdentity(ExternalKey key) implements EquipmentLookup {
	}
}
