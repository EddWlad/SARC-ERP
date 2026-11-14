package com.tidsec.sarc_bakend.modules.consumption.infrastructure.parser;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.shared.catalog.ExternalKey;
import com.tidsec.sarc_bakend.shared.importing.batch.RowIssue;
import com.tidsec.sarc_bakend.shared.importing.reading.DateTimeText;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;
import com.tidsec.sarc_bakend.shared.importing.reading.TabularRow;
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
	private static final Pattern UTC_OFFSET = Pattern.compile("UTC\\s*([+-]\\d{1,2}(?::\\d{2})?)(?![\\d:])");
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
		Declaration start = declaration(layout.header(START));
		return start.offset() != null ? start.offset() : defaultZone;
	}

	/** La del encabezado de TERMINO si la declara; si no, la resuelta para el inicio. */
	@Override
	protected ZoneId endZone(HeaderLayout layout, ZoneId startZone) {
		Declaration end = declaration(layout.header(END));
		return end.offset() != null ? end.offset() : startZone;
	}

	@Override
	protected List<RowIssue> headerIssues(HeaderLayout layout) {
		List<RowIssue> issues = new ArrayList<>();
		for (String column : List.of(START, END)) {
			if (declaration(layout.header(column)).invalid()) {
				String header = layout.header(column);
				issues.add(new RowIssue("SOURCE_OFFSET_INVALID", "El encabezado \"" + header
						+ "\" declara una zona UTC ilegible, fuera de rango o contradictoria; corrija el archivo"));
			}
		}
		return issues;
	}

	/** Lo que declara un encabezado: nada, un desplazamiento valido o algo que no se puede usar. */
	private record Declaration(ZoneOffset offset, boolean invalid) {
	}

	private static Declaration declaration(String header) {
		String text = header == null ? "" : HeaderLayout.normalize(header);
		if (!text.contains("UTC")) {
			return new Declaration(null, false);
		}
		Set<ZoneOffset> offsets = new LinkedHashSet<>();
		int matches = 0;
		Matcher matcher = UTC_OFFSET.matcher(text);
		while (matcher.find()) {
			matches++;
			try {
				offsets.add(ZoneOffset.of(normalizeOffset(matcher.group(1))));
			}
			catch (DateTimeException ex) {
				return new Declaration(null, true);
			}
		}
		// Cada "UTC" debe traer un desplazamiento legible, y todos deben coincidir.
		int declared = text.split("UTC", -1).length - 1;
		if (offsets.size() != 1 || declared != matches) {
			return new Declaration(null, true);
		}
		return new Declaration(offsets.iterator().next(), false);
	}

	/** "+5" o "-05" se escriben como "+05:00" para que ZoneOffset los acepte sin ambiguedad. */
	private static String normalizeOffset(String raw) {
		String sign = raw.substring(0, 1);
		String[] parts = raw.substring(1).split(":");
		String hours = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
		return sign + hours + ":" + (parts.length > 1 ? parts[1] : "00");
	}

	@Override
	protected SourceFields sourceFields(HeaderLayout layout, TabularRow row) {
		String site = blankToNull(layout.value(row, SITE));
		String charger = blankToNull(layout.value(row, CHARGER));
		return new SourceFields(site, charger, null, blankToNull(layout.value(row, CONNECTOR)),
				new EquipmentLookup.ByIdentity(new ExternalKey(site, charger)));
	}

	/** Enerlink reporta sesiones iniciadas sin energia ni termino: la ausencia no invalida la fila. */
	@Override
	protected boolean admitsMissingMeasurements() {
		return true;
	}
}
