package com.tidsec.sarc_bakend.modules.intermittency.domain.model.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

class WindowTransitionsTest {

	private static final UUID POINT = UUID.randomUUID();
	private static final UUID ACTOR = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-20T15:00:30Z");

	private static Instant t(String time) {
		return Instant.parse("2026-10-15T" + time + ":00Z");
	}

	private static IntermittencyWindow closed(String start, String end) {
		return IntermittencyWindow.register(POINT, "QA008 Punto (900001)", t(start), t(end), null,
				WindowSource.MANUAL, null, false, NOW);
	}

	private static IntermittencyWindow open(String start) {
		return IntermittencyWindow.register(POINT, "QA008 Punto (900001)", t(start), null, null, WindowSource.MANUAL,
				null, false, NOW);
	}

	@Test
	void aClosedWindowUnder240MinutesIsConfirmedWithItsDuration() {
		IntermittencyWindow window = closed("10:00", "13:59");

		assertThat(window.getState()).isEqualTo(WindowState.CONFIRMED);
		assertThat(window.getDurationMinutes()).isEqualTo(239);
		assertThat(window.getReviewReasons()).isEmpty();
		assertThat(window.getCurrentRevision()).isEqualTo(1);
	}

	@Test
	void aClosedWindowOf240MinutesOrMoreGoesToReviewWithLongWindow() {
		IntermittencyWindow window = closed("10:00", "14:00");

		assertThat(window.getState()).isEqualTo(WindowState.REVIEW);
		assertThat(window.getReviewReasons()).containsExactly(ReviewReason.LONG_WINDOW);
	}

	@Test
	void aWindowWithoutEndIsOpenWithoutDuration() {
		IntermittencyWindow window = open("16:01");

		assertThat(window.getState()).isEqualTo(WindowState.OPEN);
		assertThat(window.getEndAt()).isNull();
		assertThat(window.getDurationMinutes()).isNull();
		assertThat(window.isOpen()).isTrue();
	}

	@Test
	void closingAnOpenWindowConfirmsItOrSendsItToReview() {
		IntermittencyWindow shortOne = open("10:00");
		WindowRevision revision = shortOne.close(t("10:45"), ACTOR, NOW);

		assertThat(shortOne.getState()).isEqualTo(WindowState.CONFIRMED);
		assertThat(shortOne.getDurationMinutes()).isEqualTo(45);
		assertThat(shortOne.getCurrentRevision()).isEqualTo(2);
		assertThat(revision.getChange()).isEqualTo(WindowChange.CLOSED);
		assertThat(revision.getRevision()).isEqualTo(2);
		assertThat(revision.getActorId()).isEqualTo(ACTOR);

		IntermittencyWindow longOne = open("08:00");
		longOne.close(t("12:00"), ACTOR, NOW);
		assertThat(longOne.getState()).isEqualTo(WindowState.REVIEW);
		assertThat(longOne.getReviewReasons()).containsExactly(ReviewReason.LONG_WINDOW);
	}

	@Test
	void onlyAnOpenWindowCanBeClosed() {
		assertThatThrownBy(() -> closed("10:00", "11:00").close(t("12:00"), ACTOR, NOW))
				.isInstanceOfSatisfying(BusinessRuleException.class,
						ex -> assertThat(ex.code()).isEqualTo("WINDOW_NOT_OPEN"));
	}

	@Test
	void anEndBeforeOrEqualToTheStartIsRejected() {
		assertCode(() -> closed("10:00", "09:00"), "WINDOW_END_BEFORE_START");
		assertCode(() -> closed("10:00", "10:00"), "WINDOW_END_BEFORE_START");
		assertCode(() -> open("10:00").close(t("10:00"), ACTOR, NOW), "WINDOW_END_BEFORE_START");
		assertCode(() -> open("10:00").close(t("09:59"), ACTOR, NOW), "WINDOW_END_BEFORE_START");
	}

	@Test
	void futureInstantsAreRejectedExceptTheCurrentMinute() {
		Instant now = Instant.parse("2026-10-15T14:05:20Z");

		IntermittencyWindow ok = IntermittencyWindow.register(POINT, "P", t("13:00"), t("14:05"), null,
				WindowSource.MANUAL, null, false, now);
		assertThat(ok.getState()).isEqualTo(WindowState.CONFIRMED);
		assertCode(() -> IntermittencyWindow.register(POINT, "P", t("13:00"), t("14:06"), null, WindowSource.MANUAL,
				null, false, now), "WINDOW_FUTURE_INSTANT");
		assertCode(() -> IntermittencyWindow.register(POINT, "P", t("14:06"), null, null, WindowSource.MANUAL, null,
				false, now), "WINDOW_FUTURE_INSTANT");
		IntermittencyWindow pending = IntermittencyWindow.register(POINT, "P", t("14:00"), null, null,
				WindowSource.MANUAL, null, false, now);
		assertCode(() -> pending.close(t("14:07"), ACTOR, now), "WINDOW_FUTURE_INSTANT");
	}

	@Test
	void instantsMustBeInExactMinutes() {
		assertCode(() -> IntermittencyWindow.register(POINT, "P", Instant.parse("2026-10-15T10:00:30Z"), null, null,
				WindowSource.MANUAL, null, false, NOW), "WINDOW_INSTANT_NOT_MINUTE");
		assertCode(() -> IntermittencyWindow.register(POINT, "P", t("10:00"), Instant.parse("2026-10-15T11:00:01Z"),
				null, WindowSource.MANUAL, null, false, NOW), "WINDOW_INSTANT_NOT_MINUTE");
	}

	@Test
	void aTestWindowIsAlwaysClosedAndKeepsItsReviewReasons() {
		assertCode(() -> IntermittencyWindow.register(POINT, "P", t("10:00"), null, null, WindowSource.XLSX, null,
				true, NOW), "OPEN_TEST_NOT_ALLOWED");
		IntermittencyWindow window = IntermittencyWindow.register(POINT, "P", t("08:00"), t("13:00"), null,
				WindowSource.XLSX, new ImportProvenance(UUID.randomUUID(), 3, 1), true, NOW);

		assertThat(window.getState()).isEqualTo(WindowState.TEST);
		assertThat(window.getReviewReasons()).containsExactly(ReviewReason.LONG_WINDOW);
		assertThat(window.getRowNumber()).isEqualTo(3);
		assertThat(window.getPairNumber()).isEqualTo(1);
	}

	@Test
	void correctionRecalculatesAndMayReturnToReview() {
		IntermittencyWindow window = closed("10:00", "11:00");

		WindowRevision revision = window.correct(null, t("14:30"), "Fin corregido", "El fin era otro", ACTOR, NOW);

		assertThat(window.getState()).isEqualTo(WindowState.REVIEW);
		assertThat(window.getDurationMinutes()).isEqualTo(270);
		assertThat(window.getComment()).isEqualTo("Fin corregido");
		assertThat(revision.getChange()).isEqualTo(WindowChange.CORRECTED);
		assertThat(revision.getJustification()).isEqualTo("El fin era otro");
		assertCode(() -> closed("10:00", "11:00").correct(null, t("12:00"), null, " ", ACTOR, NOW),
				"JUSTIFICATION_REQUIRED");
	}

	@Test
	void aFailedCorrectionLeavesTheWindowAsItWas() {
		IntermittencyWindow window = closed("10:00", "11:00");

		assertCode(() -> window.correct(t("11:30"), null, null, "Inicio nuevo", ACTOR, NOW),
				"WINDOW_END_BEFORE_START");

		assertThat(window.getStartAt()).isEqualTo(t("10:00"));
		assertThat(window.getCurrentRevision()).isEqualTo(1);
	}

	@Test
	void markingTestFromReviewKeepsReasonsAndUnmarkingReturnsToReview() {
		IntermittencyWindow window = closed("08:00", "13:00");

		WindowRevision marked = window.markTest("Prueba del proveedor", ACTOR, NOW);
		assertThat(window.getState()).isEqualTo(WindowState.TEST);
		assertThat(window.getReviewReasons()).containsExactly(ReviewReason.LONG_WINDOW);
		assertThat(marked.getChange()).isEqualTo(WindowChange.MARKED_TEST);

		WindowRevision unmarked = window.unmarkTest("Era un corte real", ACTOR, NOW);
		assertThat(window.getState()).isEqualTo(WindowState.REVIEW);
		assertThat(unmarked.getChange()).isEqualTo(WindowChange.UNMARKED_TEST);
		assertThat(window.getCurrentRevision()).isEqualTo(3);
	}

	@Test
	void confirmingAReviewWindowClearsItsReasonsAndNeedsAJustification() {
		IntermittencyWindow window = closed("08:00", "13:00");

		assertCode(() -> window.confirmReview("", ACTOR, NOW), "JUSTIFICATION_REQUIRED");
		window.confirmReview("Verificado con el proveedor", ACTOR, NOW);

		assertThat(window.getState()).isEqualTo(WindowState.CONFIRMED);
		assertThat(window.getReviewReasons()).isEmpty();
		assertCode(() -> closed("10:00", "11:00").confirmReview("x", ACTOR, NOW), "WINDOW_NOT_IN_REVIEW");
	}

	@Test
	void anAnnulledWindowKeepsItsDataAndCannotChangeAgain() {
		IntermittencyWindow window = open("10:00");

		window.annul("Registrada por error", ACTOR, NOW);

		assertThat(window.getState()).isEqualTo(WindowState.ANNULLED);
		assertThat(window.getStartAt()).isEqualTo(t("10:00"));
		assertCode(() -> window.annul("otra vez", ACTOR, NOW), "WINDOW_ALREADY_ANNULLED");
		assertCode(() -> window.markTest("x", ACTOR, NOW), "WINDOW_NOT_MARKABLE");
		assertCode(() -> window.correct(null, t("11:00"), null, "x", ACTOR, NOW), "WINDOW_NOT_CORRECTABLE");
	}

	@Test
	void everyRevisionCarriesTheValuesOfItsMoment() {
		IntermittencyWindow window = closed("10:00", "11:00");
		WindowRevision first = window.snapshot(WindowChange.CREATED, null, ACTOR, NOW);
		window.correct(null, t("11:30"), null, "Fin corregido", ACTOR, NOW);
		WindowRevision second = window.snapshot(WindowChange.CORRECTED, "Fin corregido", ACTOR, NOW);

		assertThat(first.getRevision()).isEqualTo(1);
		assertThat(first.getEndAt()).isEqualTo(t("11:00"));
		assertThat(first.getDurationMinutes()).isEqualTo(60);
		assertThat(second.getRevision()).isEqualTo(2);
		assertThat(second.getEndAt()).isEqualTo(t("11:30"));
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class,
				ex -> assertThat(ex.code()).isEqualTo(code));
	}
}
