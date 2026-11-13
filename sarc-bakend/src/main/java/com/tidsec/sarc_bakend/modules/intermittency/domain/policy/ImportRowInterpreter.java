package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowEdit;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

/**
 * Convierte una fila cruda y sus ediciones en ventanas interpretadas, con sus observaciones (contracts/
 * import-format.md, research R4). Es puro y se recalcula completo despues de cada edicion o decision.
 *
 * <p>Nunca suma un dia de forma automatica: un fin anterior o igual al inicio queda invalido. El fin del dia
 * siguiente y el fin con fecha completa solo vienen de las ediciones (FR-024, FR-026). Detecta los duplicados y
 * solapamientos de una misma fila; los de filas distintas y los de lo ya guardado los resuelve el clasificador.
 */
public final class ImportRowInterpreter {

	private static final Pattern EXCEL_DATE_TIME = Pattern
			.compile("^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}):(\\d{2}):(\\d{2})$");
	private static final Pattern DECIMAL = Pattern.compile("^\\d+(?:[.,]\\d+)?$");
	private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$");
	private static final Pattern TOTAL_CLOCK = Pattern.compile("^(\\d+):(\\d{2})(?::(\\d{2}))?$");
	private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d/M/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);
	private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
	private static final LocalDate TIME_CELL_DAY = LocalDate.of(1899, 12, 31);
	private static final int MIN_SERIAL = 36_526;
	private static final int MAX_SERIAL = 73_415;

	private final ZoneId zone;

	public ImportRowInterpreter(ZoneId zone) {
		this.zone = zone;
	}

	/** Resultado de leer una hora: los minutos del dia, o el motivo por el que no se pudo. */
	private record ClockTime(Integer minutes, IssueCode issue, boolean blank) {
	}

	/**
	 * Interpreta la fila. {@code previous} aporta las decisiones ya tomadas por par y el total acordado, que se
	 * conservan mientras sigan siendo aplicables.
	 */
	public RowInterpretation interpret(RawRow raw, List<RowEdit> edits, RowInterpretation previous, Instant now) {
		String dateText = raw.get(RawRow.DATE);
		String code = raw.get(RawRow.CODE).strip();
		String name = raw.get(RawRow.NAME).strip();
		String comment = raw.get(RawRow.COMMENT).strip();
		Boolean rowTest = null;
		Map<Integer, Boolean> pairTest = new HashMap<>();
		Set<Integer> removed = new LinkedHashSet<>();
		Map<Integer, Map<String, String>> windowEdits = new HashMap<>();
		TreeSet<Integer> pairs = new TreeSet<>(raw.pairs());
		for (RowEdit edit : edits) {
			switch (edit.target()) {
				case RowEdit.DATE -> dateText = edit.value() == null ? "" : edit.value().strip();
				case RowEdit.CODE -> code = edit.value() == null ? "" : edit.value().strip();
				case RowEdit.COMMENT -> comment = edit.value() == null ? "" : edit.value().strip();
				case RowEdit.TEST -> {
					boolean on = Boolean.parseBoolean(edit.value());
					if (edit.pair() == null) {
						rowTest = on;
						pairTest.clear();
					}
					else {
						pairTest.put(edit.pair(), on);
					}
				}
				case RowEdit.WINDOW, RowEdit.ADD_WINDOW -> {
					windowEdits.computeIfAbsent(edit.pair(), key -> new HashMap<>()).put(edit.field(), edit.value());
					if (RowEdit.ADD_WINDOW.equals(edit.target())) {
						pairs.add(edit.pair());
						removed.remove(edit.pair());
					}
				}
				case RowEdit.REMOVE_PAIR -> removed.add(edit.pair());
				default -> {
					// Una edicion desconocida no cambia la interpretacion.
				}
			}
		}

		LocalDate day = parseDate(dateText);
		Map<Integer, WindowResolution> decisions = new HashMap<>();
		if (previous != null) {
			previous.windows().forEach(window -> {
				if (window.decision() != null) {
					decisions.put(window.pair(), window.decision());
				}
			});
		}

		List<RowWindow> windows = new ArrayList<>();
		for (int pair : pairs) {
			if (removed.contains(pair)) {
				continue;
			}
			boolean test = pairTest.containsKey(pair) ? pairTest.get(pair) : Boolean.TRUE.equals(rowTest);
			RowWindow window = window(raw, pair, windowEdits.getOrDefault(pair, Map.of()), day, test,
					decisions.get(pair), now);
			if (window != null) {
				windows.add(window);
			}
		}

		windows = merge(windows);
		windows = markRowConflicts(windows);

		List<IssueCode> rowIssues = new ArrayList<>();
		if (day == null) {
			rowIssues.add(IssueCode.DATE_FORMAT);
		}
		if (code.isEmpty()) {
			rowIssues.add(IssueCode.MISSING_CODE);
		}
		Integer fileTotal = parseTotalMinutes(raw.get(RawRow.TOTAL));
		if (fileTotal == null) {
			rowIssues.add(IssueCode.TOTAL_MISSING);
		}
		int[] totals = totals(windows);
		int computed = totals[0];
		// Con duplicadas sin resolver se muestran los dos totales y basta que el del archivo coincida con uno.
		Integer withDuplicates = totals[1] == totals[0] ? null : totals[1];
		if (fileTotal != null && fileTotal.intValue() != computed
				&& (withDuplicates == null || fileTotal.intValue() != withDuplicates.intValue())) {
			rowIssues.add(IssueCode.TOTAL_MISMATCH);
		}
		boolean acknowledged = previous != null && previous.totalAcknowledged();
		return new RowInterpretation(day == null ? null : day.toString(), code, name, comment, windows, rowIssues,
				fileTotal, computed, withDuplicates, acknowledged);
	}

	/** Vuelve a calcular el total y los motivos de fila tras un cambio de decisiones (OMIT, MERGE, OPEN...). */
	public RowInterpretation refresh(RawRow raw, List<RowEdit> edits, RowInterpretation current, Instant now) {
		return interpret(raw, edits, current, now);
	}

	// ----- Ventanas -----

	private RowWindow window(RawRow raw, int pair, Map<String, String> edited, LocalDate day, boolean test,
			WindowResolution decision, Instant now) {
		String startText = raw.get(RawRow.start(pair));
		String endText = raw.get(RawRow.end(pair));
		boolean added = !raw.pairs().contains(pair);
		boolean endsNextDay = Boolean.parseBoolean(edited.get(RowEdit.ENDS_NEXT_DAY));
		String editedStart = edited.get(RowEdit.START_AT);
		String editedEnd = edited.get(RowEdit.END_AT);

		ClockTime start = editedStart != null ? new ClockTime(null, null, false) : clock(startText);
		ClockTime end = editedEnd != null ? new ClockTime(null, null, false) : clock(endText);
		boolean startBlank = editedStart == null && start.blank();
		boolean endBlank = editedEnd == null && end.blank();
		if (startBlank && endBlank && !added && edited.isEmpty()) {
			return null;
		}

		List<IssueCode> issues = new ArrayList<>();
		if (start.issue() != null) {
			issues.add(start.issue());
		}
		if (end.issue() != null && !issues.contains(end.issue())) {
			issues.add(end.issue());
		}

		Instant startAt = null;
		Instant endAt = null;
		if (editedStart != null) {
			startAt = instant(editedStart);
			if (startAt == null) {
				issues.add(IssueCode.TIME_FORMAT);
			}
		}
		else if (day != null && start.minutes() != null) {
			startAt = atMinute(day, start.minutes());
		}
		if (editedEnd != null) {
			endAt = instant(editedEnd);
			if (endAt == null) {
				issues.add(IssueCode.TIME_FORMAT);
			}
		}
		else if (day != null && end.minutes() != null) {
			endAt = atMinute(endsNextDay ? day.plusDays(1) : day, end.minutes());
		}

		boolean openDecision = decision == WindowResolution.OPEN;
		boolean openable = !startBlank && endBlank && issues.isEmpty();
		if (openDecision && !openable) {
			decision = null;
			openDecision = false;
		}
		if (decision == WindowResolution.OPEN && test) {
			decision = null;
			openDecision = false;
		}
		if (openDecision) {
			endAt = null;
		}
		else if ((startBlank ^ endBlank) && issues.isEmpty()) {
			issues.add(IssueCode.INCOMPLETE_PAIR);
		}
		else if (startBlank && endBlank && issues.isEmpty()) {
			issues.add(IssueCode.INCOMPLETE_PAIR);
		}

		Integer minutes = null;
		if (startAt != null && endAt != null) {
			if (!endAt.isAfter(startAt)) {
				issues.add(IssueCode.END_BEFORE_START);
				if (endAt.equals(startAt)) {
					issues.add(IssueCode.ZERO_DURATION);
				}
			}
			else {
				minutes = (int) Duration.between(startAt, endAt).toMinutes();
			}
		}
		if ((startAt != null && FutureInstantPolicy.isFuture(startAt, now))
				|| (endAt != null && FutureInstantPolicy.isFuture(endAt, now))) {
			issues.add(IssueCode.FUTURE_INSTANT);
		}
		if (minutes != null && issues.isEmpty() && LongWindowPolicy.isLong(minutes)) {
			issues.add(IssueCode.LONG_WINDOW);
		}
		RowClassification worst = RowClassification.worst(issues.stream().map(IssueCode::classification).toList());
		return new RowWindow(pair, startText, endText, startAt == null ? null : iso(startAt),
				endAt == null ? null : iso(endAt), issues.stream().anyMatch(code -> code.classification()
						== RowClassification.INVALID) ? null : minutes, endsNextDay, test, added, decision, null,
				null, issues, worst);
	}

	private Instant atMinute(LocalDate day, int minutes) {
		return day.atStartOfDay(zone).toInstant().plus(minutes, ChronoUnit.MINUTES);
	}

	private String iso(Instant instant) {
		return OffsetDateTime.ofInstant(instant, zone).toString();
	}

	private static Instant instant(String text) {
		try {
			return OffsetDateTime.parse(text.strip()).toInstant();
		}
		catch (DateTimeParseException invalid) {
			return null;
		}
	}

	// ----- Fusion y conflictos dentro de la fila -----

	/** Una ventana con MERGE se une a las de la fila con las que se solapa; queda una sola que abarca a todas. */
	private List<RowWindow> merge(List<RowWindow> windows) {
		List<RowWindow> result = new ArrayList<>(windows);
		for (RowWindow requested : windows) {
			RowWindow now = current(result, requested.pair());
			if (requested.decision() != WindowResolution.MERGE || now.mergedIntoPair() != null || !closedShape(now)) {
				continue;
			}
			List<RowWindow> group = new ArrayList<>(List.of(now));
			boolean grew = true;
			while (grew) {
				grew = false;
				for (RowWindow candidate : result) {
					if (!group.contains(candidate) && candidate.mergedIntoPair() == null && closedShape(candidate)
							&& group.stream().anyMatch(member -> overlap(member, candidate))) {
						group.add(candidate);
						grew = true;
					}
				}
			}
			if (group.size() < 2) {
				continue;
			}
			RowWindow target = group.stream().min(Comparator.comparing(RowWindow::startInstant)
					.thenComparingInt(RowWindow::pair)).orElseThrow();
			Instant start = group.stream().map(RowWindow::startInstant).min(Comparator.naturalOrder()).orElseThrow();
			Instant end = group.stream().map(RowWindow::endInstant).max(Comparator.naturalOrder()).orElseThrow();
			int minutes = (int) Duration.between(start, end).toMinutes();
			for (RowWindow member : group) {
				int index = indexOf(result, member.pair());
				if (member.pair() == target.pair()) {
					List<IssueCode> issues = new ArrayList<>(member.issues());
					issues.removeIf(code -> code == IssueCode.LONG_WINDOW);
					if (LongWindowPolicy.isLong(minutes)) {
						issues.add(IssueCode.LONG_WINDOW);
					}
					result.set(index, member.withTimes(iso(start), iso(end), minutes, null).withIssues(issues, null));
				}
				else {
					result.set(index, member.withTimes(member.startAt(), member.endAt(), member.minutes(),
							target.pair()));
				}
			}
		}
		return result;
	}

	private static RowWindow current(List<RowWindow> windows, int pair) {
		return windows.get(indexOf(windows, pair));
	}

	private static int indexOf(List<RowWindow> windows, int pair) {
		for (int i = 0; i < windows.size(); i++) {
			if (windows.get(i).pair() == pair) {
				return i;
			}
		}
		throw new IllegalStateException("Par inexistente: " + pair);
	}

	private static boolean closedShape(RowWindow window) {
		return window.startAt() != null && window.endAt() != null && window.minutes() != null
				&& window.issues().stream().noneMatch(code -> code.classification() == RowClassification.INVALID);
	}

	/** Duplicadas y solapadas dentro de la fila, entre las ventanas que cuentan. */
	private List<RowWindow> markRowConflicts(List<RowWindow> windows) {
		List<RowWindow> result = new ArrayList<>(windows);
		for (int i = 0; i < windows.size(); i++) {
			RowWindow left = windows.get(i);
			if (left.ignored() || !left.hasShape()) {
				continue;
			}
			for (int j = i + 1; j < windows.size(); j++) {
				RowWindow right = windows.get(j);
				if (right.ignored() || !right.hasShape() || !overlap(left, right)) {
					continue;
				}
				IssueCode code = identical(left, right) ? IssueCode.DUPLICATE_IN_BATCH : IssueCode.OVERLAP_IN_BATCH;
				result.set(i, withIssue(result.get(i), code));
				result.set(j, withIssue(result.get(j), code));
			}
		}
		return result;
	}

	private static RowWindow withIssue(RowWindow window, IssueCode code) {
		if (window.issues().contains(code)) {
			return window;
		}
		List<IssueCode> issues = new ArrayList<>(window.issues());
		issues.add(code);
		return window.withIssues(issues, window.conflictWindowId());
	}

	static boolean identical(RowWindow left, RowWindow right) {
		return left.startAt() != null && java.util.Objects.equals(left.startInstant(), right.startInstant())
				&& java.util.Objects.equals(left.endInstant(), right.endInstant());
	}

	/** Solape de {@code [inicio, fin)}; sin fin (ventana abierta) el rango llega al infinito. */
	static boolean overlap(RowWindow left, RowWindow right) {
		Instant leftEnd = left.endInstant() == null ? Instant.MAX : left.endInstant();
		Instant rightEnd = right.endInstant() == null ? Instant.MAX : right.endInstant();
		return left.startInstant().isBefore(rightEnd) && right.startInstant().isBefore(leftEnd);
	}

	/**
	 * Total recalculado: la suma de las ventanas validas que no estan ignoradas. {@code [0]} cuenta una sola vez
	 * cada grupo de duplicadas; {@code [1]} las cuenta todas (el total con el duplicado, mientras sin resolver).
	 */
	private static int[] totals(List<RowWindow> windows) {
		int deduplicated = 0;
		int all = 0;
		Set<String> seen = new LinkedHashSet<>();
		for (RowWindow window : windows) {
			if (window.ignored() || window.minutes() == null) {
				continue;
			}
			all += window.minutes();
			if (window.has(IssueCode.DUPLICATE_IN_BATCH)) {
				if (!seen.add(window.startAt() + "|" + window.endAt())) {
					continue;
				}
			}
			deduplicated += window.minutes();
		}
		return new int[] { deduplicated, all };
	}

	// ----- Lectura de valores -----

	/** Fecha de la fila: celda de fecha de Excel, dd/MM/yyyy, yyyy-MM-dd o un numero de serie. Nula si no vale. */
	static LocalDate parseDate(String raw) {
		String text = raw == null ? "" : raw.strip();
		if (text.isEmpty()) {
			return null;
		}
		try {
			Matcher excel = EXCEL_DATE_TIME.matcher(text);
			if (excel.matches()) {
				boolean midnight = excel.group(2).equals("00") && excel.group(3).equals("00")
						&& excel.group(4).equals("00");
				return midnight ? LocalDate.parse(excel.group(1)) : null;
			}
			if (text.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
				return LocalDate.parse(text);
			}
			if (text.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$")) {
				return LocalDate.parse(text, DAY_MONTH_YEAR);
			}
			if (text.matches("^\\d{5}(?:\\.0+)?$")) {
				int serial = Integer.parseInt(text.replaceAll("\\..*$", ""));
				return serial >= MIN_SERIAL && serial <= MAX_SERIAL ? EXCEL_EPOCH.plusDays(serial) : null;
			}
		}
		catch (DateTimeParseException invalid) {
			return null;
		}
		return null;
	}

	/** Hora de una ventana: celda con formato de hora, fraccion del dia o {@code H:mm}. */
	private static ClockTime clock(String raw) {
		String text = raw == null ? "" : raw.strip();
		if (text.isEmpty()) {
			return new ClockTime(null, null, true);
		}
		Matcher excel = EXCEL_DATE_TIME.matcher(text);
		if (excel.matches()) {
			LocalDate day = LocalDate.parse(excel.group(1));
			if (!day.equals(TIME_CELL_DAY) && !day.equals(EXCEL_EPOCH)) {
				return new ClockTime(null, IssueCode.TIME_FORMAT, false);
			}
			return roundToMinute(Integer.parseInt(excel.group(2)) * 3600.0 + Integer.parseInt(excel.group(3)) * 60.0
					+ Integer.parseInt(excel.group(4)));
		}
		if (DECIMAL.matcher(text).matches()) {
			double fraction = Double.parseDouble(text.replace(',', '.'));
			return fraction >= 1 ? new ClockTime(null, IssueCode.TIME_FORMAT, false)
					: roundToMinute(fraction * 86_400.0);
		}
		Matcher clock = CLOCK.matcher(text);
		if (clock.matches()) {
			int hours = Integer.parseInt(clock.group(1));
			int minutes = Integer.parseInt(clock.group(2));
			int seconds = clock.group(3) == null ? 0 : Integer.parseInt(clock.group(3));
			if (hours > 23 || minutes > 59 || seconds > 59) {
				return new ClockTime(null, IssueCode.TIME_FORMAT, false);
			}
			return roundToMinute(hours * 3600.0 + minutes * 60.0 + seconds);
		}
		return new ClockTime(null, IssueCode.TIME_FORMAT, false);
	}

	/** Con segundos {@code s}: el minuto es {@code round(s/60)} si {@code |s - 60m| < 1}; si no, es ambiguo. */
	private static ClockTime roundToMinute(double seconds) {
		long minute = Math.round(seconds / 60.0);
		if (Math.abs(seconds - 60.0 * minute) < 1.0) {
			return new ClockTime((int) minute, null, false);
		}
		return new ClockTime(null, IssueCode.SECONDS_AMBIGUOUS, false);
	}

	/**
	 * Total inactivo del archivo en minutos: una hora {@code h:mm}, una fraccion del dia o una celda de duracion
	 * ({@code [h]:mm}), que llega como fecha y hora a partir del 31/12/1899 y se toma como dias y horas. Nulo si
	 * viene vacio o no se entiende.
	 */
	static Integer parseTotalMinutes(String raw) {
		String text = raw == null ? "" : raw.strip();
		if (text.isEmpty()) {
			return null;
		}
		Matcher excel = EXCEL_DATE_TIME.matcher(text);
		if (excel.matches()) {
			LocalDate day = LocalDate.parse(excel.group(1));
			long days = Math.max(0, ChronoUnit.DAYS.between(TIME_CELL_DAY, day));
			double seconds = days * 86_400.0 + Integer.parseInt(excel.group(2)) * 3600.0
					+ Integer.parseInt(excel.group(3)) * 60.0 + Integer.parseInt(excel.group(4));
			return (int) Math.round(seconds / 60.0);
		}
		if (DECIMAL.matcher(text).matches()) {
			return (int) Math.round(Double.parseDouble(text.replace(',', '.')) * 1440.0);
		}
		Matcher clock = TOTAL_CLOCK.matcher(text);
		if (clock.matches()) {
			double seconds = Integer.parseInt(clock.group(1)) * 3600.0 + Integer.parseInt(clock.group(2)) * 60.0
					+ (clock.group(3) == null ? 0 : Integer.parseInt(clock.group(3)));
			return (int) Math.round(seconds / 60.0);
		}
		return null;
	}
}
