package com.tidsec.sarc_bakend.modules.uptime.infrastructure.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.application.port.UptimeRowParser;
import com.tidsec.sarc_bakend.modules.uptime.domain.model.measurement.UptimeBasis;
import com.tidsec.sarc_bakend.modules.uptime.domain.policy.MeasurementRules;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.DateTimeText;
import com.tidsec.sarc_bakend.shared.importing.reading.DecimalText;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.reading.InvalidValueException;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularSheet;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Reporte "Uptime y Uso" de Enerlink y el informe semanal que se envia por correo, armado con sus
 * columnas: una hoja por grupo (Electrolineras, Puntos de carga), sin Activo ni Cargando (D09,
 * excepcion de la Constitucion 1.2.0). Sin la columna Ocupado/Cargando el uptime es el que informa el
 * archivo ({@link UptimeBasis#REPORTED_PERCENT}). El contexto del informe no sustituye los limites de Desde y Hasta. Una columna rotulada en horas se lleva al minuto mas cercano y el valor
 * original queda en {@code sourceValues}; en minutos, una cifra fraccionaria pasa tal cual y la observa
 * {@code MeasurementRules}.
 */
@Component
class EnerlinkUptimeParser implements UptimeRowParser {

	/** Separa las filas de cada hoja en el lote: la hoja 2, fila 5 es la 200005. */
	static final int SHEET_STRIDE = 100_000;

	private static final String SITE = "site";
	private static final String CHARGER = "charger";
	private static final String CONNECTOR = "connector";
	private static final String START = "start";
	private static final String END = "end";
	private static final String AVAILABLE = "available";
	private static final String OCCUPIED = "occupied";
	private static final String FAULT = "fault";
	private static final String OFFLINE = "offline";
	private static final String PERCENT = "percent";
	private static final char DECIMAL_SEPARATOR = '.';
	private static final int MAX_TEXT = 150;
	private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
	private static final List<HeaderLayout.Column> COLUMNS = List.of(new HeaderLayout.Column(CHARGER, "CARGADOR"),
			new HeaderLayout.Column(CONNECTOR, "CONECTOR"), new HeaderLayout.Column(AVAILABLE, "DISPONIBLE"),
			new HeaderLayout.Column(FAULT, "CON FALLA", "EN FALLA"),
			new HeaderLayout.Column(OFFLINE, "FUERA DE LINEA"), new HeaderLayout.Column(PERCENT, "UPTIME"));
	private static final List<HeaderLayout.Column> OPTIONAL = List.of(new HeaderLayout.Column(SITE, "ACTIVO"),
			new HeaderLayout.Column(OCCUPIED, "OCUPADO", "CARGANDO"),
			new HeaderLayout.Column(START, "DESDE", "INICIO"), new HeaderLayout.Column(END, "HASTA", "TERMINO"));
	private static final List<DateTimeFormatter> FORMATS = List.of(DateTimeText.pattern("dd-MM-uuuu HH:mm:ss"),
			DateTimeText.pattern("dd/MM/uuuu HH:mm:ss"), DateTimeText.pattern("dd-MM-uuuu HH:mm"), DateTimeText.EXCEL);
	private static final List<String> HOUR_MARKS = List.of("(HRS)", "(HR)", "(H)", "(HORAS)");

	private final ZoneId zone;

	EnerlinkUptimeParser(OperatingZone operatingZone) {
		this.zone = operatingZone.zone();
	}

	@Override
	public List<ParsedRow> parse(List<TabularSheet> sheets, DeclaredPeriod period) {
        return parseSource(sheets,period,false);
    }
    @Override
    public List<ParsedRow> inspectSource(List<TabularSheet> sheets) {
        return parseSource(sheets,null,true);
    }
    private List<ParsedRow> parseSource(List<TabularSheet> sheets,DeclaredPeriod period,boolean inspection) {
		List<TabularSheet> used = sheets.stream().filter(sheet -> !sheet.rows().isEmpty()).toList();
		boolean several = used.size() > 1;
		List<ParsedRow> parsed = new ArrayList<>();
		for (int index = 0; index < used.size(); index++) {
			TabularSheet sheet = used.get(index);
			HeaderLayout layout = layout(sheet);
			if (!inspection && period == null && !(layout.has(START) && layout.has(END))) {
				throw new BusinessRuleException("UPTIME_PERIOD_MISSING",
						"El archivo no indica el período: elige la semana del informe o agrega las columnas Desde y Hasta.");
			}
			int offset = several ? (index + 1) * SHEET_STRIDE : 0;
			String name = sheet.name().isBlank() ? null : sheet.name();
			layout.dataRows().stream().filter(row -> !row.isBlank())
					.forEach(row -> parsed.add(parseRow(layout, row, offset, name, period, inspection)));
		}
		return parsed;
	}

	/** En un libro de varias hojas el error dice en cual falta la columna. */
	private static HeaderLayout layout(TabularSheet sheet) {
		try {
			return HeaderLayout.locate(sheet.rows(), COLUMNS, OPTIONAL);
		}
		catch (BusinessRuleException ex) {
			if (sheet.name().isBlank()) {
				throw ex;
			}
			throw new BusinessRuleException(ex.code(), "Hoja \"" + sheet.name() + "\": " + ex.getMessage());
		}
	}

	private ParsedRow parseRow(HeaderLayout layout, TabularRow row, int offset, String sheet, DeclaredPeriod period, boolean inspection) {
		List<RowIssue> issues = new ArrayList<>();
		String site = text(layout, row, SITE, issues);
		String charger = text(layout, row, CHARGER, issues);
		String connector = text(layout, row, CONNECTOR, issues);
		boolean startProvided = layout.has(START) && !layout.value(row,START).isBlank();
        boolean endProvided = layout.has(END) && !layout.value(row,END).isBlank();
        Instant start = startProvided ? date(layout,row,START,"Desde",issues) : null;
		Instant end = endProvided ? date(layout,row,END,"Hasta",issues) : null;
        if (!inspection && period==null && !startProvided && !endProvided)
            issues.add(new RowIssue("UPTIME_PERIOD_MISSING","Indica los limites reales o la referencia del informe."));
		Map<String, Object> source = new HashMap<>();
		boolean states = layout.has(OCCUPIED);
		BigDecimal available = duration(layout, row, AVAILABLE, "Disponible", source, issues);
		BigDecimal occupied = states ? duration(layout, row, OCCUPIED, "Ocupado", source, issues) : null;
		BigDecimal fault = duration(layout, row, FAULT, "Con falla", source, issues);
		BigDecimal offline = duration(layout, row, OFFLINE, "Fuera de linea", source, issues);
		String rawPercent = layout.value(row, PERCENT).replace("%", "").strip();
		BigDecimal percent = rawPercent.isEmpty() ? null
				: read("Uptime", () -> DecimalText.parse(rawPercent, DECIMAL_SEPARATOR), issues);

		source.put("reportedPercent", rawPercent);
		source.put("sourcePeriodStartPresent",startProvided);
        source.put("sourcePeriodEndPresent",endProvided);
        if (layout.has(START)) source.put("periodStart",layout.value(row,START));
        if (layout.has(END)) source.put("periodEnd",layout.value(row,END));
		return new ParsedRow(offset + row.number(), sheet, row.number(), site, charger, connector, start, end,
				new MeasurementRules.Values(available, occupied, fault, offline, percent,
						states ? UptimeBasis.STATES : UptimeBasis.REPORTED_PERCENT),
				source, issues);
	}

	private static BigDecimal duration(HeaderLayout layout, TabularRow row, String key, String field,
			Map<String, Object> source, List<RowIssue> issues) {
		boolean hours = HOUR_MARKS.stream().anyMatch(HeaderLayout.normalize(layout.header(key))::contains);
		String raw = layout.value(row, key);
		BigDecimal value = read(field, () -> DecimalText.parse(raw, DECIMAL_SEPARATOR), issues);
		if (!hours) {
			source.put(key + "Minutes", raw);
			return value;
		}
		source.put(key + "Hours", raw);
		BigDecimal minutes = value == null ? null
				: value.multiply(MINUTES_PER_HOUR).setScale(0, RoundingMode.HALF_UP);
		source.put(key + "Minutes", minutes == null ? "" : minutes.toPlainString());
		return minutes;
	}

	private static String text(HeaderLayout layout, TabularRow row, String key, List<RowIssue> issues) {
		String value = layout.value(row, key);
		if (value.length() <= MAX_TEXT) {
			return value;
		}
		issues.add(new RowIssue("TEXT_TOO_LONG", "\"" + value.substring(0, 40) + "…\" supera 150 caracteres"));
		return value.substring(0, MAX_TEXT - 1) + "…";
	}

	private Instant date(HeaderLayout layout, TabularRow row, String key, String field, List<RowIssue> issues) {
		return read(field, () -> DateTimeText.parse(layout.value(row, key), FORMATS, zone), issues);
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
}
