package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RawRow.RawCell;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;
import com.tidsec.sarc_bakend.modules.intermittency.domain.policy.BatchWindowConflicts.Candidate;

class BatchWindowConflictsTest {

	private static final UUID ROW_A = UUID.randomUUID();
	private static final UUID ROW_B = UUID.randomUUID();
	private static final UUID ROW_C = UUID.randomUUID();

	private static Instant t(String day, String time) {
		return Instant.parse(day + "T" + time + ":00Z");
	}

	@Test
	void anOpenWindowOccupiesFromItsStartSoLaterWindowsOfOtherRowsConflict() {
		// Fila 117 con OPEN (16:01 del 7) y una fila del dia siguiente del mismo punto.
		Candidate open = new Candidate(ROW_A, 3, t("2026-10-07", "21:01"), null);
		Candidate nextDay = new Candidate(ROW_B, 1, t("2026-10-08", "13:00"), t("2026-10-08", "13:30"));
		Candidate before = new Candidate(ROW_C, 1, t("2026-10-07", "14:00"), t("2026-10-07", "14:30"));

		var conflicts = BatchWindowConflicts.detect(List.of(open, nextDay, before));

		assertThat(conflicts).containsOnlyKeys(open.key(), nextDay.key());
		assertThat(conflicts.get(open.key())).isEqualTo(IssueCode.OVERLAP_IN_BATCH);
		assertThat(conflicts.get(nextDay.key())).isEqualTo(IssueCode.OVERLAP_IN_BATCH);
	}

	@Test
	void withoutTheOpenWindowTheLaterRowIsFree() {
		Candidate nextDay = new Candidate(ROW_B, 1, t("2026-10-08", "13:00"), t("2026-10-08", "13:30"));

		assertThat(BatchWindowConflicts.detect(List.of(nextDay))).isEmpty();
	}

	@Test
	void identicalWindowsOfDifferentRowsAreDuplicatesAndPartialOnesAreOverlaps() {
		Candidate first = new Candidate(ROW_A, 1, t("2026-10-05", "13:00"), t("2026-10-05", "14:00"));
		Candidate same = new Candidate(ROW_B, 1, t("2026-10-05", "13:00"), t("2026-10-05", "14:00"));
		Candidate partial = new Candidate(ROW_C, 1, t("2026-10-05", "13:30"), t("2026-10-05", "14:30"));

		var conflicts = BatchWindowConflicts.detect(List.of(first, same, partial));

		assertThat(conflicts.get(first.key())).isEqualTo(IssueCode.DUPLICATE_IN_BATCH);
		assertThat(conflicts.get(same.key())).isEqualTo(IssueCode.DUPLICATE_IN_BATCH);
		assertThat(conflicts.get(partial.key())).isEqualTo(IssueCode.OVERLAP_IN_BATCH);
	}

	@Test
	void touchingWindowsDoNotConflictAndWindowsOfTheSameRowAreLeftToTheInterpreter() {
		Candidate first = new Candidate(ROW_A, 1, t("2026-10-05", "13:00"), t("2026-10-05", "14:00"));
		Candidate touching = new Candidate(ROW_B, 1, t("2026-10-05", "14:00"), t("2026-10-05", "15:00"));
		Candidate sameRow = new Candidate(ROW_A, 2, t("2026-10-05", "13:30"), t("2026-10-05", "13:45"));

		assertThat(BatchWindowConflicts.detect(List.of(first, touching, sameRow))).isEmpty();
	}

	// ----- Resolucion de filas -----

	private static RowInterpretation interpretation(String... times) {
		java.util.List<RawCell> cells = new java.util.ArrayList<>(List.of(new RawCell(RawRow.DATE, "Fecha", "2026-10-05"),
				new RawCell(RawRow.CODE, "Codigo", "900001"), new RawCell(RawRow.NAME, "Nombre", ""),
				new RawCell(RawRow.COMMENT, "Comentario", ""), new RawCell(RawRow.TOTAL, "Total", "")));
		for (int i = 0; i + 1 < times.length; i += 2) {
			cells.add(new RawCell(RawRow.start(i / 2 + 1), "Inicio", times[i]));
			cells.add(new RawCell(RawRow.end(i / 2 + 1), "Fin", times[i + 1]));
		}
		return new ImportRowInterpreter(ZoneId.of("America/Guayaquil")).interpret(new RawRow(3, cells), List.of(), null,
				Instant.parse("2026-11-10T20:00:00Z"));
	}

	@Test
	void aRowIsResolvedByItselfWhenItIsNewOrIdenticalOrOnlyLong() {
		RowInterpretation clean = interpretation("08:00", "08:30");
		assertThat(RowResolution.isResolved(null, clean.classification(), clean)).isTrue();
		assertThat(RowResolution.isResolved(null, RowClassification.IDENTICAL, clean)).isTrue();
		RowInterpretation longWindow = interpretation("08:00", "13:00");
		assertThat(longWindow.classification()).isEqualTo(RowClassification.REVIEW);
		assertThat(RowResolution.isResolved(null, longWindow.classification(), longWindow)).isTrue();
	}

	@Test
	void anInvalidRowOnlyResolvesByBeingSkipped() {
		RowInterpretation invalid = interpretation("19:41", "19:26");

		assertThat(RowResolution.isResolved(null, invalid.classification(), invalid)).isFalse();
		assertThat(RowResolution.isResolved(RowDecision.APPLY, invalid.classification(), invalid)).isFalse();
		assertThat(RowResolution.isResolved(RowDecision.KEEP_PENDING, invalid.classification(), invalid)).isFalse();
		assertThat(RowResolution.isResolved(RowDecision.SKIP, invalid.classification(), invalid)).isTrue();
	}

	@Test
	void aPendingLinkRowResolvesByBeingKeptPendingAndAnOutOfScopeRowOnlyBySkipping() {
		RowInterpretation row = interpretation("08:00", "08:30");

		assertThat(RowResolution.isResolved(null, RowClassification.PENDING_LINK, row)).isFalse();
		assertThat(RowResolution.isResolved(RowDecision.KEEP_PENDING, RowClassification.PENDING_LINK, row)).isTrue();
		assertThat(RowResolution.isResolved(RowDecision.KEEP_PENDING, RowClassification.OUT_OF_SCOPE, row)).isFalse();
		assertThat(RowResolution.isResolved(RowDecision.SKIP, RowClassification.OUT_OF_SCOPE, row)).isTrue();
	}

	@Test
	void aStoredConflictResolvesOnlyByReplacingTheOneWindowItOverlaps() {
		RowInterpretation base = interpretation("08:00", "08:30");
		RowWindow conflicting = base.windows().getFirst().withIssues(List.of(IssueCode.OVERLAPS_EXISTING),
				UUID.randomUUID().toString());
		RowInterpretation conflict = base.withWindows(List.of(conflicting));

		assertThat(conflict.classification()).isEqualTo(RowClassification.CONFLICT);
		assertThat(RowResolution.isResolved(null, RowClassification.CONFLICT, conflict)).isFalse();
		RowInterpretation replaced = base.withWindows(List.of(conflicting.withDecision(WindowResolution.REPLACE)));
		assertThat(RowResolution.isResolved(null, RowClassification.CONFLICT, replaced)).isTrue();
		// Un solapamiento dentro del lote no se resuelve reemplazando.
		RowWindow inBatch = base.windows().getFirst().withIssues(List.of(IssueCode.OVERLAP_IN_BATCH), null)
				.withDecision(WindowResolution.REPLACE);
		assertThat(RowResolution.isResolved(null, RowClassification.CONFLICT, base.withWindows(List.of(inBatch))))
				.isFalse();
	}

	@Test
	void aTotalMismatchNeedsToBeAcknowledgedOrTheRowSkipped() {
		RowInterpretation row = interpretation("08:00", "08:30").withRowIssues(List.of(IssueCode.TOTAL_MISMATCH));

		assertThat(RowResolution.isResolved(null, RowClassification.REVIEW, row)).isFalse();
		assertThat(RowResolution.isResolved(null, RowClassification.REVIEW, row.withTotalAcknowledged(true))).isTrue();
		assertThat(RowResolution.isResolved(RowDecision.SKIP, RowClassification.REVIEW, row)).isTrue();
	}

	@Test
	void onlyUndecidedOrApplyRowsAreAppliedOnConfirm() {
		assertThat(RowResolution.appliesOnConfirm(null)).isTrue();
		assertThat(RowResolution.appliesOnConfirm(RowDecision.APPLY)).isTrue();
		assertThat(RowResolution.appliesOnConfirm(RowDecision.SKIP)).isFalse();
		assertThat(RowResolution.appliesOnConfirm(RowDecision.KEEP_PENDING)).isFalse();
	}
}
