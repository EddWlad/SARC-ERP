package com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser;

import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.ASSIGNED_CODE;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.BRAND;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.CONNECTOR_1;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.CONNECTOR_2;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.CONNECTOR_3;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.ENERLINK_CHARGER;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.ENERLINK_SCOPE;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.LOCATION;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.MAPS_URL;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.NAME;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.NETWORK;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.OCPP_ID;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.OCPP_URL;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.SERIAL;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.TYPE;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.UID_PUK;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.USAGE_STATUS;
import static com.tidsec.sarc_bakend.modules.inventory.infrastructure.parser.EquipmentSheetColumns.WALLBOX_ID;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.inventory.application.port.EquipmentSheetParser;
import com.tidsec.sarc_bakend.modules.inventory.domain.model.importbatch.EquipmentLine;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;

/** Lee la plantilla de SARC: ubica sus columnas por nombre y entrega cada fila como texto. */
@Component
class TemplateEquipmentSheetParser implements EquipmentSheetParser {

	@Override
	public List<SheetLine> parse(List<TabularRow> rows) {
		HeaderLayout layout = HeaderLayout.locate(rows, EquipmentSheetColumns.layout());
		return layout.dataRows().stream()
				.filter(row -> !row.isBlank())
				.map(row -> new SheetLine(row.number(), line(layout, row)))
				.toList();
	}

	private static EquipmentLine line(HeaderLayout layout, TabularRow row) {
		return new EquipmentLine(cell(layout, row, NETWORK), cell(layout, row, TYPE), cell(layout, row, BRAND),
				cell(layout, row, NAME), cell(layout, row, SERIAL), cell(layout, row, ASSIGNED_CODE),
				cell(layout, row, UID_PUK), cell(layout, row, LOCATION), cell(layout, row, MAPS_URL),
				cell(layout, row, OCPP_URL), cell(layout, row, USAGE_STATUS), cell(layout, row, CONNECTOR_1),
				cell(layout, row, CONNECTOR_2), cell(layout, row, CONNECTOR_3), cell(layout, row, ENERLINK_SCOPE),
				cell(layout, row, ENERLINK_CHARGER), cell(layout, row, WALLBOX_ID), cell(layout, row, OCPP_ID));
	}

	/** Celda recortada; vacia = null. */
	private static String cell(HeaderLayout layout, TabularRow row, EquipmentSheetColumns column) {
		String value = layout.value(row, column.name());
		return value.isEmpty() ? null : value;
	}
}
