package com.tidsec.sarc_bakend.modules.uptime.infrastructure.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.uptime.application.port.MeasurementBlockParser;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.importing.reading.HeaderLayout;

/**
 * Bloque de texto copiado de Enerlink para un conector ya elegido. Lee por etiqueta, en cualquier
 * orden; lo que no se pueda leer sin duda se rechaza en vez de adivinar. El formato exacto queda por
 * contrastar con la muestra real (DP-04b).
 */
@Component
class EnerlinkBlockParser implements MeasurementBlockParser {

	private static final String AVAILABLE = "DISPONIBLE";
	private static final String OCCUPIED = "OCUPADO";
	private static final String FAULT = "CON FALLA";
	private static final String OFFLINE = "FUERA DE LINEA";
	private static final Map<String, String> LABELS = labels();
	private static final Pattern LABEL = Pattern.compile(String.join("|", LABELS.keySet()));
	private static final String NUMBER = "(\\d+(?:[.,]\\d+)?)";
	private static final Pattern PERCENT = Pattern.compile("(?:UPTIME\\s*:?\\s*)?" + NUMBER + "\\s*%");
	private static final Pattern VALUE = Pattern.compile("[:=]?\\s*" + NUMBER + "\\s*(?:MIN|MINUTOS)?\\.?");
	/** Como Enerlink lo copia: "163 hrs 35 mins - Disponible", la duracion antes de la etiqueta. */
	private static final Pattern DURATION_FIRST = Pattern.compile(
			"(?:(\\d+)\\s*(?:HRS?|HORAS?)(?:\\s*(\\d+)\\s*(?:MINS?|MINUTOS?))?|(\\d+)\\s*(?:MINS?|MINUTOS?))\\s*[-–—]\\s*("
					+ LABEL.pattern() + ")");

	@Override
	public Block parse(String text) {
		String normalized = HeaderLayout.normalize(text);
		Matcher percents = PERCENT.matcher(normalized);
		BigDecimal percent = null;
		int found = 0;
		while (percents.find()) {
			percent = decimal(percents.group(1));
			found++;
		}
		String withoutPercent = percents.replaceAll(" ");
		Map<String, BigDecimal> values = DURATION_FIRST.matcher(withoutPercent).find() ? durationsFirst(withoutPercent)
				: values(withoutPercent);
		if (found > 1) {
			throw ambiguous(values.keySet());
		}
		return new Block(values.get(AVAILABLE), values.get(OCCUPIED), values.get(FAULT), values.get(OFFLINE), percent);
	}

	/** El tramo entre una etiqueta y la siguiente debe ser exactamente su valor. */
	private static Map<String, BigDecimal> values(String text) {
		Matcher labels = LABEL.matcher(text);
		List<String> detected = new ArrayList<>();
		List<int[]> spans = new ArrayList<>();
		while (labels.find()) {
			detected.add(labels.group());
			spans.add(new int[] { labels.end(), text.length() });
			if (spans.size() > 1) {
				spans.get(spans.size() - 2)[1] = labels.start();
			}
		}
		Map<String, BigDecimal> values = new LinkedHashMap<>();
		boolean clear = true;
		for (int index = 0; index < detected.size(); index++) {
			Matcher value = VALUE.matcher(text.substring(spans.get(index)[0], spans.get(index)[1]).strip());
			clear &= value.matches() && values.putIfAbsent(detected.get(index), decimal(value.group(1))) == null;
		}
		if (!clear || values.size() < LABELS.size()) {
			throw ambiguous(detected);
		}
		return values;
	}

	/** Cada etiqueta una vez, con su duracion delante; cualquier texto sobrante o faltante se rechaza. */
	private static Map<String, BigDecimal> durationsFirst(String text) {
		Matcher durations = DURATION_FIRST.matcher(text);
		List<String> detected = new ArrayList<>();
		Map<String, BigDecimal> values = new LinkedHashMap<>();
		boolean clear = true;
		while (durations.find()) {
			BigDecimal minutes = durations.group(1) == null ? new BigDecimal(durations.group(3))
					: new BigDecimal(durations.group(1)).multiply(BigDecimal.valueOf(60))
							.add(durations.group(2) == null ? BigDecimal.ZERO : new BigDecimal(durations.group(2)));
			detected.add(durations.group(4));
			clear &= values.putIfAbsent(durations.group(4), minutes) == null;
		}
		clear &= durations.replaceAll(" ").isBlank();
		if (!clear || values.size() < LABELS.size()) {
			throw ambiguous(detected);
		}
		return values;
	}

	private static BigDecimal decimal(String text) {
		return new BigDecimal(text.replace(',', '.'));
	}

	private static BusinessRuleException ambiguous(Iterable<String> detected) {
		List<String> found = new ArrayList<>();
		detected.forEach(label -> {
			if (!found.contains(LABELS.get(label))) {
				found.add(LABELS.get(label));
			}
		});
		List<String> missing = LABELS.values().stream().filter(label -> !found.contains(label)).toList();
		return new BusinessRuleException("UPTIME_BLOCK_AMBIGUOUS",
				"El bloque no se pudo interpretar sin duda; revisa las cuatro etiquetas y sus valores.",
				Map.of("detected", List.copyOf(found), "missing", missing));
	}

	private static Map<String, String> labels() {
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put(AVAILABLE, "Disponible");
		labels.put(OCCUPIED, "Ocupado");
		labels.put(FAULT, "Con falla");
		labels.put(OFFLINE, "Fuera de línea");
		return labels;
	}
}
