package com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser;

import java.util.Arrays;
import java.util.List;

import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;

/**
 * Columnas de la plantilla de carga masiva, en su orden. La plantilla las escribe y el lector las
 * busca por nombre (sin importar orden, mayusculas ni tildes), asi que se definen en un solo lugar.
 */
public enum EquipmentSheetColumns {

	NETWORK("Red", true, "KIA, JAC o CONECTZ"),
	TYPE("Tipo", true, "Punto de carga o Electrolinera"),
	BRAND("Marca", true, "Benny o Wallbox"),
	NAME("Nombre", true, "Nombre del equipo (máx. 150 caracteres)"),
	SERIAL("Serie", true, "Única en todo el inventario: identifica al equipo"),
	ASSIGNED_CODE("Código asignado", false, "Solo puntos KIA: 6 dígitos sin cero inicial. En los demás es la serie"),
	UID_PUK("UID/PUK", false, "Obligatorio en puntos de carga"),
	LOCATION("Ubicación", false, "Dirección o referencia (máx. 300 caracteres)"),
	MAPS_URL("Enlace Google Maps", false, "https://..."),
	OCPP_URL("URL OCPP", false, "ws://, wss://, http:// o https://"),
	USAGE_STATUS("Estado de uso", false,
			"Obligatorio en puntos de carga: Libre, Bloqueado o Desconectado. Vacío en electrolineras"),
	CONNECTOR_1("Conector 1", true, "Código del tipo de conector (hoja Listas)"),
	CONNECTOR_2("Conector 2", false, "Solo electrolineras"),
	CONNECTOR_3("Conector 3", false, "Solo electrolineras"),
	ENERLINK_SCOPE("Enerlink activo", false, "Columna ACTIVO del reporte de Enerlink"),
	ENERLINK_CHARGER("Enerlink cargador", false, "Columna CARGADOR del reporte de Enerlink"),
	WALLBOX_ID("Wallbox ID", false, "Identificador del cargador en Wallbox, solo si difiere de la serie"),
	OCPP_ID("OCPP ID", false, "Charge point id en el servidor OCPP (no la URL)");

	private final String header;
	private final boolean required;
	private final String hint;

	EquipmentSheetColumns(String header, boolean required, String hint) {
		this.header = header;
		this.required = required;
		this.hint = hint;
	}

	public String header() {
		return header;
	}

	/** Obligatoria en un equipo nuevo; en uno existente las celdas vacias conservan su valor. */
	public boolean required() {
		return required;
	}

	public String hint() {
		return hint;
	}

	/** Columnas que el lector exige en el encabezado: todas las de la plantilla. */
	static List<HeaderLayout.Column> layout() {
		return Arrays.stream(values()).map(column -> new HeaderLayout.Column(column.name(), column.header)).toList();
	}
}
