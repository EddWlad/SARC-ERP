package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.application.port.ParsedSession;
import com.tidsec.sarc_bakend.modules.consumption.application.port.SessionReportParser;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.policy.SessionRules;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.DateTimeText;
import com.tidsec.sarc_bakend.shared.importing.reading.DecimalText;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.reading.InvalidValueException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;

/**
 * Pasos comunes a los reportes tabulares (Template Method): ubicar encabezados, leer ID, inicio,
 * termino y energia con el formato de la fuente, y validar la sesion. Cada fuente define sus
 * columnas, formatos y como identifica al equipo.
 */
abstract class TabularSessionParser implements SessionReportParser {

	protected static final String ID = "id";
	protected static final String START = "start";
	protected static final String END = "end";
	protected static final String ENERGY = "energy";

	private static final int MAX_ID_LENGTH = 100;
	private static final int MAX_NAME_LENGTH = 150;

	/** Lo que cada fuente aporta sobre el equipo y la ubicacion de la sesion. */
	protected record SourceFields(String site, String charger, String serial, String connector,
			EquipmentLookup lookup) {
	}

	@Override
	public List<ParsedSession> parse(List<TabularRow> rows) {
		HeaderLayout layout = HeaderLayout.locate(rows, columns());
		ZoneId zone = zone(layout);
		return layout.dataRows().stream().map(row -> parseRow(layout, row, zone)).toList();
	}

	protected abstract List<HeaderLayout.Column> columns();

	protected abstract char decimalSeparator();

	protected abstract List<DateTimeFormatter> dateFormats();

	/** Zona en que estan escritas las fechas del reporte. */
	protected abstract ZoneId zone(HeaderLayout layout);

	protected abstract SourceFields sourceFields(HeaderLayout layout, TabularRow row);

	private ParsedSession parseRow(HeaderLayout layout, TabularRow row, ZoneId zone) {
		List<RowIssue> issues = new ArrayList<>();
		String id = layout.value(row, ID);
		if (id.isEmpty()) {
			issues.add(new RowIssue("SESSION_ID_REQUIRED", "Falta el identificador de la sesion"));
		}
		else if (id.length() > MAX_ID_LENGTH) {
			issues.add(new RowIssue("SESSION_ID_TOO_LONG", "El identificador de la sesion supera 100 caracteres"));
		}
		Instant start = read("Inicio", () -> DateTimeText.parse(layout.value(row, START), dateFormats(), zone), issues);
		Instant end = read("Termino", () -> DateTimeText.parse(layout.value(row, END), dateFormats(), zone), issues);
		BigDecimal energy = read("Energia",
				() -> DecimalText.parse(layout.value(row, ENERGY), decimalSeparator()), issues);
		SourceFields fields = sourceFields(layout, row);
		if (fields.lookup() == null) {
			issues.add(new RowIssue("EQUIPMENT_REFERENCE_REQUIRED", "Falta el identificador del cargador"));
		}
		for (String text : new String[] { fields.site(), fields.charger(), fields.serial(), fields.connector() }) {
			if (text != null && text.length() > MAX_NAME_LENGTH) {
				issues.add(new RowIssue("TEXT_TOO_LONG", "\"" + text + "\" supera 150 caracteres"));
			}
		}
		if (!issues.isEmpty()) {
			return new ParsedSession(row.number(), blankToNull(id), null, fields.lookup(), issues);
		}
		SessionReading reading = new SessionReading(start, end, energy, fields.site(), fields.charger(),
				fields.serial(), fields.connector());
		issues.addAll(SessionRules.check(reading));
		return new ParsedSession(row.number(), id, issues.isEmpty() ? reading : null, fields.lookup(), issues);
	}

	private static <T> T read(String field, Supplier<T> parser, List<RowIssue> issues) {
		try {
			return parser.get();
		}
		catch (InvalidValueException ex) {
			issues.add(new RowIssue(ex.code(), field + ": " + ex.getMessage()));
			return null;
		}
	}

	protected static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
