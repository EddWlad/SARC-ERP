package com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Celdas de una fila de la plantilla de equipos tal como llegaron (texto sin interpretar). Se
 * guardan asi para mostrarlas en la vista previa aunque no sean validas, y se vuelven a interpretar
 * al confirmar con las mismas reglas. Una celda vacia es null.
 */
@Embeddable
public record EquipmentLine(
		@Column(length = 40) String network,
		@Column(length = 40) String infrastructure,
		@Column(length = 40) String brand,
		@Column(length = 150) String name,
		@Column(length = 80) String serial,
		@Column(length = 80) String assignedCode,
		@Column(length = 80) String uidPuk,
		@Column(length = 300) String location,
		@Column(length = 500) String mapsUrl,
		@Column(length = 500) String ocppUrl,
		@Column(length = 40) String usageStatus,
		@Column(name = "connector_1", length = 40) String connector1,
		@Column(name = "connector_2", length = 40) String connector2,
		@Column(name = "connector_3", length = 40) String connector3,
		@Column(length = 100) String enerlinkScope,
		@Column(length = 150) String enerlinkCharger,
		@Column(length = 150) String wallboxId,
		@Column(length = 150) String ocppId) {

	/**
	 * Copia recortada al tamano de cada columna, para guardar la fila tal como llego aunque traiga
	 * textos demasiado largos (esa fila ya quedo invalida por el limite del propio dato).
	 */
	public EquipmentLine fitted() {
		return new EquipmentLine(cut(network, 40), cut(infrastructure, 40), cut(brand, 40), cut(name, 150),
				cut(serial, 80), cut(assignedCode, 80), cut(uidPuk, 80), cut(location, 300), cut(mapsUrl, 500),
				cut(ocppUrl, 500), cut(usageStatus, 40), cut(connector1, 40), cut(connector2, 40), cut(connector3, 40),
				cut(enerlinkScope, 100), cut(enerlinkCharger, 150), cut(wallboxId, 150), cut(ocppId, 150));
	}

	private static String cut(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}
}
