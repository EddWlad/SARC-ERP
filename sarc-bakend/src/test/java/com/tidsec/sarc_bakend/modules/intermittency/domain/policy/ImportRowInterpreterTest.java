package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow.RawCell;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowEdit;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

class ImportRowInterpreterTest {

	private static final Instant NOW = Instant.parse("2026-11-10T20:00:00Z");
	private static final UUID ACTOR = UUID.randomUUID();

	private final ImportRowInterpreter interpreter = new ImportRowInterpreter(ZoneId.of("America/Guayaquil"));

	/** Una fila cruda con la fecha, el codigo, los pares (inicio, fin, ...) y el total, tal como llegarian. */
	private static RawRow row(String date, String code, String total, String... times) {
		List<RawCell> cells = new ArrayList<>();
		cells.add(new RawCell(RawRow.DATE, "Fecha", date));
		cells.add(new RawCell(RawRow.CODE, "Código cargador", code));
		cells.add(new RawCell(RawRow.NAME, "Nombre cargador", "Punto"));
		cells.add(new RawCell(RawRow.COMMENT, "Comentario", "Intermitencia"));
		for (int i = 0; i + 1 < times.length; i += 2) {
			int pair = i / 2 + 1;
			cells.add(new RawCell(RawRow.start(pair), "Inicio " + pair, times[i]));
			cells.add(new RawCell(RawRow.end(pair), "Fin " + pair, times[i + 1]));
		}
		cells.add(new RawCell(RawRow.TOTAL, "Total inactivo", total));
		return new RawRow(3, cells);
	}

	private RowInterpretation read(RawRow raw, RowEdit... edits) {
		return interpreter.interpret(raw, List.of(edits), null, NOW);
	}

	private static RowEdit edit(String target, Integer pair, String field, String value) {
		return new RowEdit(target, pair, field, null, value, ACTOR, NOW.toString());
	}

	private static RowInterpretation decide(RowInterpretation current, int pair, WindowResolution resolution) {
		return current.withWindows(current.windows().stream()
				.map(window -> window.pair() == pair ? window.withDecision(resolution) : window).toList());
	}

	private static RowWindow pair(RowInterpretation interpretation, int pair) {
		return interpretation.windows().stream().filter(window -> window.pair() == pair).findFirst().orElseThrow();
	}

	@Test
	void row3HasTwoIdenticalWindowsThatAreOnlyChargedOnceUntilOneIsOmitted() {
		RawRow raw = row("2026-10-01 00:00:00", "900001", "1:49", "12:35", "13:02", "16:49", "17:19", "17:55",
				"18:21", "17:55", "18:21");

		RowInterpretation before = read(raw);

		assertThat(before.windows()).hasSize(4);
		assertThat(pair(before, 3).issues()).contains(IssueCode.DUPLICATE_IN_BATCH);
		assertThat(pair(before, 4).issues()).contains(IssueCode.DUPLICATE_IN_BATCH);
		assertThat(before.computedTotalMinutes()).isEqualTo(83);
		assertThat(before.computedWithDuplicatesMinutes()).isEqualTo(109);
		assertThat(before.fileTotalMinutes()).isEqualTo(109);
		assertThat(before.rowIssues()).doesNotContain(IssueCode.TOTAL_MISMATCH);
		assertThat(before.classification()).isEqualTo(RowClassification.CONFLICT);

		RowInterpretation after = interpreter.interpret(raw, List.of(),
				decide(before, 4, WindowResolution.OMIT), NOW);

		assertThat(pair(after, 3).issues()).doesNotContain(IssueCode.DUPLICATE_IN_BATCH);
		assertThat(after.computedTotalMinutes()).isEqualTo(83);
		assertThat(after.computedWithDuplicatesMinutes()).isNull();
		assertThat(after.rowIssues()).contains(IssueCode.TOTAL_MISMATCH);
		assertThat(after.classification()).isEqualTo(RowClassification.REVIEW);
	}

	@Test
	void acknowledgingTheTotalClearsTheReviewAfterTheDuplicateIsOmitted() {
		RawRow raw = row("2026-10-01 00:00:00", "900001", "1:49", "17:55", "18:21", "17:55", "18:21");
		RowInterpretation omitted = interpreter.interpret(raw, List.of(),
				decide(read(raw), 2, WindowResolution.OMIT), NOW);

		assertThat(omitted.classification()).isEqualTo(RowClassification.REVIEW);
		assertThat(omitted.withTotalAcknowledged(true).classification()).isEqualTo(RowClassification.NEW);
	}

	@Test
	void anEndBeforeTheStartIsInvalidAndNeverGetsAnAutomaticDay() {
		RowInterpretation row61 = read(row("2026-10-04", "900002", "23:53", "08:10", "08:40", "19:41", "19:26"));

		assertThat(pair(row61, 1).minutes()).isEqualTo(30);
		assertThat(pair(row61, 2).issues()).containsExactly(IssueCode.END_BEFORE_START);
		assertThat(pair(row61, 2).minutes()).isNull();
		assertThat(row61.classification()).isEqualTo(RowClassification.INVALID);
	}

	@Test
	void endsNextDayAndAFullEndDateComeOnlyFromEdits() {
		RawRow raw = row("2026-10-04", "900007", "14:06", "17:00", "07:05");

		RowWindow plain = pair(read(raw), 1);
		assertThat(plain.issues()).contains(IssueCode.END_BEFORE_START);

		RowWindow nextDay = pair(read(raw, edit(RowEdit.WINDOW, 1, RowEdit.ENDS_NEXT_DAY, "true")), 1);
		assertThat(nextDay.minutes()).isEqualTo(14 * 60 + 5);
		assertThat(nextDay.issues()).containsExactly(IssueCode.LONG_WINDOW);
		assertThat(nextDay.endAt()).startsWith("2026-10-05T07:05");

		RowWindow multiDay = pair(read(raw, edit(RowEdit.WINDOW, 1, RowEdit.END_AT, "2026-10-07T07:05:00-05:00")), 1);
		assertThat(multiDay.minutes()).isEqualTo(3 * 1440 - 10 * 60 + 5);
	}

	@Test
	void row163CrossesMidnightAndGoesToReviewBecauseItIsLong() {
		RowInterpretation row = read(row("2026-10-12", "900007", "16:19", "20:03", "12:22"),
				edit(RowEdit.WINDOW, 1, RowEdit.ENDS_NEXT_DAY, "true"));

		assertThat(pair(row, 1).minutes()).isEqualTo(979);
		assertThat(pair(row, 1).issues()).containsExactly(IssueCode.LONG_WINDOW);
		assertThat(row.classification()).isEqualTo(RowClassification.REVIEW);
		assertThat(row.rowIssues()).doesNotContain(IssueCode.TOTAL_MISMATCH);
	}

	@Test
	void row99HasATotalThatDoesNotMatchItsWindows() {
		RowInterpretation row = read(row("2026-10-07", "900006", "22:11", "17:07", "17:59", "18:04", "18:50"));

		assertThat(row.computedTotalMinutes()).isEqualTo(98);
		assertThat(row.fileTotalMinutes()).isEqualTo(1331);
		assertThat(row.rowIssues()).contains(IssueCode.TOTAL_MISMATCH);
		assertThat(row.classification()).isEqualTo(RowClassification.REVIEW);
	}

	@Test
	void anIncompletePairAdmitsTheOpenDecisionOnlyWhenTheEndIsMissing() {
		RawRow raw = row("2026-10-07", "900001", "0:25", "09:00", "09:10", "12:00", "12:15", "16:01", "");

		RowInterpretation pending = read(raw);
		assertThat(pair(pending, 3).issues()).containsExactly(IssueCode.INCOMPLETE_PAIR);
		assertThat(pending.rowIssues()).doesNotContain(IssueCode.TOTAL_MISMATCH);
		assertThat(pending.classification()).isEqualTo(RowClassification.INVALID);

		RowInterpretation open = interpreter.interpret(raw, List.of(), decide(pending, 3, WindowResolution.OPEN), NOW);
		assertThat(pair(open, 3).issues()).isEmpty();
		assertThat(pair(open, 3).endAt()).isNull();
		assertThat(pair(open, 3).minutes()).isNull();
		assertThat(pair(open, 3).decision()).isEqualTo(WindowResolution.OPEN);
		assertThat(open.classification()).isEqualTo(RowClassification.NEW);
		assertThat(open.computedTotalMinutes()).isEqualTo(25);

		// Un par al que le falta el inicio no se puede abrir.
		RawRow noStart = row("2026-10-07", "900001", "", "", "16:01");
		RowInterpretation stays = interpreter.interpret(noStart, List.of(),
				decide(read(noStart), 1, WindowResolution.OPEN), NOW);
		assertThat(pair(stays, 1).decision()).isNull();
		assertThat(pair(stays, 1).issues()).contains(IssueCode.INCOMPLETE_PAIR);
	}

	@Test
	void aTestWindowCannotStayOpen() {
		RawRow raw = row("2026-10-07", "900001", "", "16:01", "");
		RowInterpretation open = interpreter.interpret(raw, List.of(), decide(read(raw), 1, WindowResolution.OPEN), NOW);
		assertThat(pair(open, 1).decision()).isEqualTo(WindowResolution.OPEN);

		RowInterpretation marked = interpreter.interpret(raw, List.of(edit(RowEdit.TEST, 1, null, "true")), open, NOW);

		assertThat(pair(marked, 1).test()).isTrue();
		assertThat(pair(marked, 1).decision()).isNull();
		assertThat(pair(marked, 1).issues()).contains(IssueCode.INCOMPLETE_PAIR);
	}

	@Test
	void overlappingWindowsOfARowAreConflictsAndMergeIntoOne() {
		RawRow raw = row("2026-10-17", "900003", "0:52", "02:24", "02:30", "02:25", "03:11");

		RowInterpretation overlap = read(raw);
		assertThat(pair(overlap, 1).issues()).containsExactly(IssueCode.OVERLAP_IN_BATCH);
		assertThat(pair(overlap, 2).issues()).containsExactly(IssueCode.OVERLAP_IN_BATCH);
		assertThat(overlap.computedTotalMinutes()).isEqualTo(52);
		assertThat(overlap.rowIssues()).doesNotContain(IssueCode.TOTAL_MISMATCH);

		RowInterpretation merged = interpreter.interpret(raw, List.of(), decide(overlap, 2, WindowResolution.MERGE), NOW);
		assertThat(pair(merged, 1).minutes()).isEqualTo(47);
		assertThat(pair(merged, 1).startAt()).contains("T02:24");
		assertThat(pair(merged, 1).endAt()).contains("T03:11");
		assertThat(pair(merged, 1).issues()).isEmpty();
		assertThat(pair(merged, 2).mergedIntoPair()).isEqualTo(1);
		assertThat(pair(merged, 2).ignored()).isTrue();
		assertThat(merged.active()).hasSize(1);
		assertThat(merged.computedTotalMinutes()).isEqualTo(47);
	}

	@Test
	void keepsEverySevenPairsWithoutALimitOfFive() {
		RowInterpretation row = read(row("2026-10-02", "900002", "1:10", "06:00", "06:10", "07:00", "07:10", "08:00",
				"08:10", "09:00", "09:10", "10:00", "10:10", "11:00", "11:10", "12:00", "12:10"));

		assertThat(row.windows()).hasSize(7);
		assertThat(row.computedTotalMinutes()).isEqualTo(70);
		assertThat(row.classification()).isEqualTo(RowClassification.NEW);
	}

	@Test
	void roundsHoursThatCarryTinyFloatingPointErrorsButNotRealSeconds() {
		// 8/24 en coma flotante queda 28799.99999 s: se redondea al minuto.
		String eight = String.valueOf(8 / 24.0);
		String nine = String.valueOf(9 / 24.0);
		RowInterpretation exact = read(row("2026-10-03", "900003", "1:00", eight, nine));
		assertThat(pair(exact, 1).minutes()).isEqualTo(60);

		RowInterpretation ambiguous = read(row("2026-10-03", "900003", "0:20", "08:00:30", "08:20:00"));
		assertThat(pair(ambiguous, 1).issues()).containsExactly(IssueCode.SECONDS_AMBIGUOUS);
		assertThat(ambiguous.classification()).isEqualTo(RowClassification.INVALID);

		// A un segundo exacto del minuto ya es ambiguo; con menos de un segundo, se redondea.
		assertThat(read(row("2026-10-03", "900003", "", "08:00:59", "09:00:00")).windows().getFirst().issues())
				.containsExactly(IssueCode.SECONDS_AMBIGUOUS);
		assertThat(read(row("2026-10-03", "900003", "", String.valueOf((8 * 3600 + 59.6) / 86400.0), "09:00"))
				.windows().getFirst().minutes()).isEqualTo(59);
	}

	@Test
	void readsTimesFromExcelTimeCellsAndFractions() {
		RowInterpretation row = read(row("2026-10-01 00:00:00", "900001", "", "1899-12-31 12:35:00",
				"1899-12-31 13:02:00", "0.5", "0.5208333333333334"));

		assertThat(pair(row, 1).minutes()).isEqualTo(27);
		assertThat(pair(row, 2).minutes()).isEqualTo(30);
		assertThat(read(row("2026-10-01", "900001", "", "1.5", "0.6")).windows().getFirst().issues())
				.containsExactly(IssueCode.TIME_FORMAT);
		assertThat(read(row("2026-10-01", "900001", "", "ocho", "09:00")).windows().getFirst().issues())
				.containsExactly(IssueCode.TIME_FORMAT);
		assertThat(read(row("2026-10-01", "900001", "", "24:30", "09:00")).windows().getFirst().issues())
				.containsExactly(IssueCode.TIME_FORMAT);
	}

	@Test
	void acceptsTheFourDateFormatsAndRejectsInvalidOnes() {
		assertThat(read(row("2026-10-01 00:00:00", "900001", "", "08:00", "09:00")).date()).isEqualTo("2026-10-01");
		assertThat(read(row("01/10/2026", "900001", "", "08:00", "09:00")).date()).isEqualTo("2026-10-01");
		assertThat(read(row("2026-10-01", "900001", "", "08:00", "09:00")).date()).isEqualTo("2026-10-01");
		assertThat(read(row("46296", "900001", "", "08:00", "09:00")).date()).isEqualTo("2026-10-01");

		for (String invalid : new String[] { "31/02/2026", "", "ayer", "2026-10-01 10:30:00", "12345", "2026-13-01" }) {
			RowInterpretation row = read(row(invalid, "900001", "", "08:00", "09:00"));
			assertThat(row.date()).as(invalid).isNull();
			assertThat(row.rowIssues()).as(invalid).contains(IssueCode.DATE_FORMAT);
			assertThat(row.classification()).as(invalid).isEqualTo(RowClassification.INVALID);
			assertThat(pair(row, 1).startAt()).isNull();
		}
	}

	@Test
	void equalStartAndEndIsZeroDurationAndEndBeforeStart() {
		RowWindow window = pair(read(row("2026-10-01", "900001", "", "10:00", "10:00")), 1);

		assertThat(window.issues()).containsExactly(IssueCode.END_BEFORE_START, IssueCode.ZERO_DURATION);
	}

	@Test
	void aMissingTotalIsOnlyInformational() {
		RowInterpretation row = read(row("2026-10-01", "900001", "", "10:00", "10:30"));

		assertThat(row.fileTotalMinutes()).isNull();
		assertThat(row.rowIssues()).containsExactly(IssueCode.TOTAL_MISSING);
		assertThat(row.classification()).isEqualTo(RowClassification.NEW);
	}

	@Test
	void readsDurationTotalsFromExcelCellsFractionsAndText() {
		assertThat(ImportRowInterpreter.parseTotalMinutes("1899-12-31 01:49:00")).isEqualTo(109);
		// 36:42 en una celda [h]:mm es 1 dia y 12:42, que POI entrega a partir del 01/01/1900.
		assertThat(ImportRowInterpreter.parseTotalMinutes("1900-01-01 12:42:00")).isEqualTo(2202);
		assertThat(ImportRowInterpreter.parseTotalMinutes("0.0756944444444444")).isEqualTo(109);
		assertThat(ImportRowInterpreter.parseTotalMinutes("1.5291666666666666")).isEqualTo(2202);
		assertThat(ImportRowInterpreter.parseTotalMinutes("36:42")).isEqualTo(2202);
		assertThat(ImportRowInterpreter.parseTotalMinutes("1:49")).isEqualTo(109);
		assertThat(ImportRowInterpreter.parseTotalMinutes("")).isNull();
		assertThat(ImportRowInterpreter.parseTotalMinutes("nada")).isNull();
	}

	@Test
	void instantsAfterTheCurrentMinuteAreInvalid() {
		Instant now = Instant.parse("2026-10-01T14:05:20Z");
		RawRow raw = row("2026-10-01", "900001", "", "09:00", "09:06");

		// 09:06 en Guayaquil son las 14:06Z: posterior al minuto en curso (14:05).
		RowWindow future = pair(interpreter.interpret(raw, List.of(), null, now), 1);
		assertThat(future.issues()).contains(IssueCode.FUTURE_INSTANT);
		RowWindow ok = pair(interpreter.interpret(row("2026-10-01", "900001", "", "09:00", "09:05"), List.of(), null,
				now), 1);
		assertThat(ok.issues()).isEmpty();
	}

	@Test
	void editsLeaveTheOriginalValuesAndAddOrRemovePairs() {
		RawRow raw = row("2026-10-01", "900001", "", "10:00", "10:30", "11:00", "11:30");

		RowInterpretation edited = read(raw, edit(RowEdit.DATE, null, null, "2026-10-02"),
				edit(RowEdit.CODE, null, null, " 900002 "), edit(RowEdit.COMMENT, null, null, "Corte"),
				edit(RowEdit.REMOVE_PAIR, 2, null, null),
				edit(RowEdit.ADD_WINDOW, 3, RowEdit.START_AT, "2026-10-02T14:00:00-05:00"),
				edit(RowEdit.ADD_WINDOW, 3, RowEdit.END_AT, "2026-10-02T14:20:00-05:00"));

		assertThat(raw.get(RawRow.DATE)).isEqualTo("2026-10-01");
		assertThat(edited.date()).isEqualTo("2026-10-02");
		assertThat(edited.code()).isEqualTo("900002");
		assertThat(edited.comment()).isEqualTo("Corte");
		assertThat(edited.windows()).extracting(RowWindow::pair).containsExactly(1, 3);
		assertThat(pair(edited, 3).added()).isTrue();
		assertThat(pair(edited, 3).minutes()).isEqualTo(20);
	}

	@Test
	void theTestFlagAppliesToTheRowOrToOnePairAndTheLastEditWins() {
		RawRow raw = row("2026-10-01", "900001", "", "10:00", "10:30", "11:00", "11:30");

		RowInterpretation all = read(raw, edit(RowEdit.TEST, null, null, "true"));
		assertThat(all.windows()).allMatch(RowWindow::test);

		RowInterpretation one = read(raw, edit(RowEdit.TEST, 2, null, "true"));
		assertThat(pair(one, 1).test()).isFalse();
		assertThat(pair(one, 2).test()).isTrue();

		RowInterpretation reverted = read(raw, edit(RowEdit.TEST, null, null, "true"),
				edit(RowEdit.TEST, 1, null, "false"));
		assertThat(pair(reverted, 1).test()).isFalse();
		assertThat(pair(reverted, 2).test()).isTrue();
	}

	@Test
	void aMissingCodeMakesTheRowPendingLink() {
		RowInterpretation row = read(row("2026-10-01", "  ", "", "10:00", "10:30"));

		assertThat(row.rowIssues()).contains(IssueCode.MISSING_CODE);
		assertThat(row.classification()).isEqualTo(RowClassification.PENDING_LINK);
	}

	@Test
	void reinterpretingKeepsTheDecisionsAndTheAcknowledgedTotal() {
		RawRow raw = row("2026-10-01", "900001", "0:10", "10:00", "10:30");
		RowInterpretation first = read(raw).withTotalAcknowledged(true);

		RowInterpretation again = interpreter.interpret(raw, List.of(edit(RowEdit.COMMENT, null, null, "x")), first, NOW);

		assertThat(again.totalAcknowledged()).isTrue();
		assertThat(again.rowIssues()).contains(IssueCode.TOTAL_MISMATCH);
		assertThat(again.classification()).isEqualTo(RowClassification.NEW);
	}
}
