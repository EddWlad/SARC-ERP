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
	/** La serie se guarda en una columna mas corta que los nombres (VARCHAR(80)). */
	private static final int MAX_SERIAL_LENGTH = 80;

	/** Lo que cada fuente aporta sobre el equipo y la ubicacion de la sesion. */
	protected record SourceFields(String site, String charger, String serial, String connector,
			EquipmentLookup lookup) {
	}

	@Override
	public List<ParsedSession> parse(List<TabularRow> rows) {
		HeaderLayout layout = HeaderLayout.locate(rows, columns());
		List<RowIssue> headerIssues = headerIssues(layout);
		if (!headerIssues.isEmpty()) {
			// Una zona mal declarada no se reemplaza por otra asumida: ninguna fila se incorpora (FR-004).
			return layout.dataRows().stream()
					.map(row -> new ParsedSession(row.number(), blankToNull(layout.value(row, ID)), null,
							sourceFields(layout, row).lookup(), headerIssues))
					.toList();
		}
		ZoneId zone = zone(layout);
		ZoneId endZone = endZone(layout, zone);
		return layout.dataRows().stream().map(row -> parseRow(layout, row, zone, endZone)).toList();
	}

	protected abstract List<HeaderLayout.Column> columns();

	protected abstract char decimalSeparator();

	protected abstract List<DateTimeFormatter> dateFormats();

	/** Zona en que esta escrito el inicio. */
	protected abstract ZoneId zone(HeaderLayout layout);

	/** Zona en que esta escrito el fin: la del inicio salvo que la fuente declare otra. */
	protected ZoneId endZone(HeaderLayout layout, ZoneId startZone) {
		return startZone;
	}

	/** Problemas de los encabezados que invalidan todas las filas (p. ej. una zona ilegible). */
	protected List<RowIssue> headerIssues(HeaderLayout layout) {
		return List.of();
	}

	protected abstract SourceFields sourceFields(HeaderLayout layout, TabularRow row);

	/** Si energia y termino pueden faltar sin invalidar la fila; solo una fuente que lo reporte asi. */
	protected boolean admitsMissingMeasurements() {
		return false;
	}

	private ParsedSession parseRow(HeaderLayout layout, TabularRow row, ZoneId zone, ZoneId endZone) {
		List<RowIssue> issues = new ArrayList<>();
		String id = layout.value(row, ID);
		if (id.isEmpty()) {
			issues.add(new RowIssue("SESSION_ID_REQUIRED", "Falta el identificador de la sesion"));
		}
		else if (id.length() > MAX_ID_LENGTH) {
			issues.add(new RowIssue("SESSION_ID_TOO_LONG", "El identificador de la sesion supera 100 caracteres"));
		}
		Instant start = read("Inicio", () -> DateTimeText.parse(layout.value(row, START), dateFormats(), zone), issues);
		Instant end = isAbsent(layout.value(row, END)) ? null
				: read("Termino", () -> DateTimeText.parse(layout.value(row, END), dateFormats(), endZone), issues);
		BigDecimal energy = isAbsent(layout.value(row, ENERGY)) ? null
				: read("Energia", () -> DecimalText.parse(layout.value(row, ENERGY), decimalSeparator()), issues);
		SourceFields fields = sourceFields(layout, row);
		if (fields.lookup() == null) {
			issues.add(new RowIssue("EQUIPMENT_REFERENCE_REQUIRED", "Falta el identificador del cargador"));
		}
		for (String text : new String[] { fields.site(), fields.charger(), fields.connector() }) {
			checkLength(text, MAX_NAME_LENGTH, issues);
		}
		checkLength(fields.serial(), MAX_SERIAL_LENGTH, issues);
		if (!issues.isEmpty()) {
			return new ParsedSession(row.number(), blankToNull(id), null, fields.lookup(), issues);
		}
		// La fecha que vale es la que escribio la fuente: se guarda el desplazamiento de cada extremo.
		SessionReading reading = new SessionReading(start, end, energy, fields.site(), fields.charger(),
				fields.serial(), fields.connector(), zone.getRules().getOffset(start),
				end == null ? null : endZone.getRules().getOffset(end));
		issues.addAll(SessionRules.check(reading));
		return new ParsedSession(row.number(), id,
				issues.stream().anyMatch(SessionRules::isBlocking) ? null : reading, fields.lookup(), issues);
	}

	private boolean isAbsent(String text) {
		return admitsMissingMeasurements() && text.replace('\u00A0', ' ').isBlank();
	}

	/** Se cita solo el comienzo del texto: un valor desbordado no debe inflar el mensaje de la fila. */
	private static void checkLength(String text, int max, List<RowIssue> issues) {
		if (text != null && text.length() > max) {
			String start = text.length() > 40 ? text.substring(0, 40) + "…" : text;
			issues.add(new RowIssue("TEXT_TOO_LONG", "\"" + start + "\" supera " + max + " caracteres"));
		}
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
