package com.tidsec.sarc_bakend.modules.inventory.domain.model.equipment;

/**
 * Datos editables de un equipo. Red y tipo no estan aqui porque definen su identidad: cambiarlos
 * es registrar otro equipo. {@code requestedCode} solo se usa en puntos KIA.
 */
public record EquipmentDetails(
		EquipmentBrand brand,
		String name,
		String serial,
		String requestedCode,
		String uidPuk,
		String location,
		String mapsUrl,
		String ocppUrl,
		UsageStatus usageStatus) {
}
