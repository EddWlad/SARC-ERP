package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.importing.DateTimeText;
import com.tidsec.sarc_bakend.shared.importing.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.TabularRow;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * "Sessions report" de Wallbox: Serial number, Charger, Session id, Start, End, Energy (kWh). Las
 * fechas no traen zona (son hora local de Ecuador), la energia usa coma decimal y el cargador se
 * identifica por su serie fisica, que es la misma del inventario.
 */
@Component
class WallboxSessionParser extends TabularSessionParser {

	private static final String SERIAL = "serial";
	private static final String CHARGER = "charger";
	private static final List<DateTimeFormatter> FORMATS = List.of(DateTimeText.EXCEL,
			DateTimeText.pattern("uuuu-MM-dd HH:mm"), DateTimeText.pattern("dd/MM/uuuu HH:mm:ss"),
			DateTimeText.pattern("dd/MM/uuuu HH:mm"));

	private final ZoneId zone;

	WallboxSessionParser(OperatingZone operatingZone) {
		this.zone = operatingZone.zone();
	}

	@Override
	public SessionSource source() {
		return SessionSource.WALLBOX;
	}

	@Override
	protected List<HeaderLayout.Column> columns() {
		return List.of(new HeaderLayout.Column(SERIAL, "Serial number"), new HeaderLayout.Column(CHARGER, "Charger"),
				new HeaderLayout.Column(ID, "Session id"), new HeaderLayout.Column(START, "Start"),
				new HeaderLayout.Column(END, "End"), new HeaderLayout.Column(ENERGY, "Energy"));
	}

	@Override
	protected char decimalSeparator() {
		return ',';
	}

	@Override
	protected List<DateTimeFormatter> dateFormats() {
		return FORMATS;
	}

	@Override
	protected ZoneId zone(HeaderLayout layout) {
		return zone;
	}

	@Override
	protected SourceFields sourceFields(HeaderLayout layout, TabularRow row) {
		String serial = blankToNull(layout.value(row, SERIAL));
		return new SourceFields(null, blankToNull(layout.value(row, CHARGER)), serial, null,
				serial == null ? null : new EquipmentLookup.BySerial(serial));
	}
}
