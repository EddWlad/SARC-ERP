package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.importing.DateTimeText;
import com.tidsec.sarc_bakend.shared.importing.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.TabularRow;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Reporte "Transacciones" de Enerlink. Se usan ID, INICIO, TERMINO, ACTIVO, CARGADOR, CONECTOR y
 * ENERGIA CARGADA; el resto de columnas (vehiculo, SOC, RFID...) se ignora. La zona viene en el
 * encabezado de las fechas, p. ej. "INICIO (UTC-05:00)". Enerlink no reporta la serie: el equipo se
 * identifica por ACTIVO + CARGADOR, registrados como identidad externa verificada en el inventario.
 */
@Component
class EnerlinkSessionParser extends TabularSessionParser {

	private static final String SITE = "site";
	private static final String CHARGER = "charger";
	private static final String CONNECTOR = "connector";
	private static final Pattern UTC_OFFSET = Pattern.compile("UTC\\s*([+-]\\d{1,2}(?::\\d{2})?)");
	private static final List<DateTimeFormatter> FORMATS = List.of(DateTimeText.pattern("dd-MM-uuuu HH:mm:ss"),
			DateTimeText.pattern("dd/MM/uuuu HH:mm:ss"), DateTimeText.pattern("dd-MM-uuuu HH:mm"),
			DateTimeText.EXCEL);

	private final ZoneId defaultZone;

	EnerlinkSessionParser(OperatingZone operatingZone) {
		this.defaultZone = operatingZone.zone();
	}

	@Override
	public SessionSource source() {
		return SessionSource.ENERLINK;
	}

	@Override
	protected List<HeaderLayout.Column> columns() {
		return List.of(new HeaderLayout.Column(ID, "ID"), new HeaderLayout.Column(START, "INICIO"),
				new HeaderLayout.Column(END, "TERMINO"), new HeaderLayout.Column(SITE, "ACTIVO"),
				new HeaderLayout.Column(CHARGER, "CARGADOR"), new HeaderLayout.Column(CONNECTOR, "CONECTOR"),
				new HeaderLayout.Column(ENERGY, "ENERGIA CARGADA"));
	}

	@Override
	protected char decimalSeparator() {
		return '.';
	}

	@Override
	protected List<DateTimeFormatter> dateFormats() {
		return FORMATS;
	}

	/** La del encabezado de INICIO si la declara; si no, la zona de operacion. */
	@Override
	protected ZoneId zone(HeaderLayout layout) {
		Matcher matcher = UTC_OFFSET.matcher(HeaderLayout.normalize(layout.header(START)));
		if (!matcher.find()) {
			return defaultZone;
		}
		try {
			return ZoneOffset.of(matcher.group(1));
		}
		catch (DateTimeException ex) {
			return defaultZone;
		}
	}

	@Override
	protected SourceFields sourceFields(HeaderLayout layout, TabularRow row) {
		String site = blankToNull(layout.value(row, SITE));
		String charger = blankToNull(layout.value(row, CHARGER));
		return new SourceFields(site, charger, null, blankToNull(layout.value(row, CONNECTOR)),
				charger == null ? null : new EquipmentLookup.ByIdentity(new ExternalKey(site, charger)));
	}
}
