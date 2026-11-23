package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.AffectedElement;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.NewTicket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Judgment;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Measure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Result;

/** FR-042, FR-045, FR-046, FR-052, P-04: duraciones y juicio de referencia, con los casos del escenario 43. */
class EpisodeDurationsTest {

	private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
	private static final Instant T0 = NOW.minusSeconds(4 * 3600);
	private static final UUID ACTOR = UUID.randomUUID();
	private static final UUID VERSION = UUID.randomUUID();

	private static Episode episode() {
		return Ticket.open(new NewTicket(9001, UUID.randomUUID(), AffectedElement.chatbot(null),
				IncidentType.CHATBOT_SERVICE_FAILURE, ReportOrigin.PLATFORM, null, "x", Priority.MEDIUM, ACTOR, "Ana",
				"CHATBOT", null, T0, "Aviso", VERSION, ACTOR, NOW)).latestEpisode();
	}

	private static void record(Episode episode, MilestoneKind kind, long minutesAfterT0) {
		episode.recordMilestone(kind, MilestoneStatus.RECORDED, T0.plusSeconds(minutesAfterT0 * 60), "Retroactivo", null,
				true, ACTOR, NOW);
	}

	private static ReferenceRule rule(Integer validation, Integer recovery, Integer escalation) {
		return ReferenceRule.of(VERSION, IncidentType.WAIT_FOR_CONNECTION, validation, recovery, false, escalation, null,
				Priority.MEDIUM);
	}

	private static TicketEvent escalation(Episode episode, TargetKind kind, long minutesAfterT0, boolean annulled) {
		TicketEvent event = TicketEvent.record(UUID.randomUUID(), episode.getId(), EventType.ESCALATED,
				T0.plusSeconds(minutesAfterT0 * 60), NOW, ACTOR, true, "Escalado", kind, "Rol", null, true);
		if (annulled) {
			event.annul(ACTOR, NOW, "Error");
		}
		return event;
	}

	private static Result escalationResult(Episode episode, List<TicketEvent> events) {
		return ReferenceEvaluator.evaluate(episode, Optional.of(rule(null, null, 60)), events, NOW).getFirst().result();
	}

	// ----- Duraciones -----

	@Test
	void calculatesTheFiveDurationsInWholeMinutesRoundedDown() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, T0.plusSeconds(5 * 60 + 59), "x", null, true,
				ACTOR, NOW);
		record(episode, MilestoneKind.T2, 20);
		record(episode, MilestoneKind.T3, 109);

		assertThat(EpisodeDurations.minutes(episode, MilestoneKind.T1, MilestoneKind.T0)).as("5 min 59 s -> 5").isEqualTo(5);
		assertThat(EpisodeDurations.minutes(episode, MilestoneKind.T2, MilestoneKind.T0)).isEqualTo(20);
		assertThat(EpisodeDurations.minutes(episode, MilestoneKind.T3, MilestoneKind.T0)).isEqualTo(109);
		assertThat(EpisodeDurations.pairs(episode)).extracting(EpisodeDurations.Pair::pair)
				.containsExactly("T1-T0", "T2-T0", "T3-T0", "T4-T0", "T4-T3");
	}

	@Test
	void aMissingMilestoneGivesNullAndNeverZeroAndNamesWhatIsMissing() {
		Episode episode = episode();
		record(episode, MilestoneKind.T1, 5);

		List<EpisodeDurations.Pair> pairs = EpisodeDurations.pairs(episode);
		assertThat(pairs.get(0).minutes()).isEqualTo(5);
		assertThat(pairs.get(1).minutes()).isNull();
		assertThat(pairs.get(1).missing()).containsExactly(MilestoneKind.T2);
		assertThat(pairs.get(2).minutes()).isNull();
		assertThat(pairs.get(4).missing()).containsExactly(MilestoneKind.T4, MilestoneKind.T3);
	}

	@Test
	void notApplicableAndNoDataMilestonesDoNotProduceADuration() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T2, MilestoneStatus.NOT_APPLICABLE, null, "No hubo apoyo", null, false, ACTOR,
				NOW);
		episode.recordMilestone(MilestoneKind.T3, MilestoneStatus.NO_DATA, null, "Sin dato", null, false, ACTOR, NOW);
		assertThat(EpisodeDurations.minutes(episode, MilestoneKind.T2, MilestoneKind.T0)).isNull();
		assertThat(EpisodeDurations.minutes(episode, MilestoneKind.T3, MilestoneKind.T0)).isNull();
	}

	@Test
	void anOpenEpisodeReportsItsElapsedTimeApartAndAClosedOneDoesNot() {
		Episode open = episode();
		assertThat(EpisodeDurations.elapsedOpenMinutes(open, NOW)).isEqualTo(240);
		assertThat(EpisodeDurations.elapsedOpenMinutes(open, T0.plusSeconds(59))).isZero();
	}

	// ----- Juicio de referencia -----

	@Test
	void validationAndRecoveryAreComparedWithTheLimitUsingExactSeconds() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, T0.plusSeconds(5 * 60), "x", null, true, ACTOR,
				NOW);
		record(episode, MilestoneKind.T3, 180);
		List<Judgment> judgments = ReferenceEvaluator.evaluate(episode, Optional.of(rule(5, 180, null)), List.of(), NOW);
		assertThat(judgments).extracting(Judgment::measure, Judgment::result).containsExactly(
				org.assertj.core.groups.Tuple.tuple(Measure.VALIDATION, Result.WITHIN),
				org.assertj.core.groups.Tuple.tuple(Measure.RECOVERY, Result.WITHIN));

		Episode late = episode();
		late.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, T0.plusSeconds(5 * 60 + 1), "x", null, true, ACTOR,
				NOW);
		record(late, MilestoneKind.T3, 181);
		assertThat(ReferenceEvaluator.evaluate(late, Optional.of(rule(5, 180, null)), List.of(), NOW))
				.extracting(Judgment::result).containsExactly(Result.OUTSIDE, Result.OUTSIDE);
	}

	@Test
	void finishingBeforeTheLowerBoundIsNotADeviation() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 2);
		assertThat(ReferenceEvaluator.evaluate(episode, Optional.of(rule(null, 300, null)), List.of(), NOW).getFirst()
				.result()).isEqualTo(Result.WITHIN);
	}

	@Test
	void aMissingMilestoneIsNotEvaluableNeverWithin() {
		Episode episode = episode();
		assertThat(ReferenceEvaluator.evaluate(episode, Optional.of(rule(5, 180, null)), List.of(), NOW))
				.extracting(Judgment::result).containsExactly(Result.NOT_EVALUABLE, Result.NOT_EVALUABLE);
	}

	@Test
	void aTypeWithoutARuleHasNoJudgments() {
		assertThat(ReferenceEvaluator.evaluate(episode(), Optional.empty(), List.of(), NOW)).isEmpty();
	}

	@Test
	void recoveryComparesT3NeverTheClosure() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 100);
		assertThat(ReferenceEvaluator.evaluate(episode, Optional.of(rule(null, 120, null)), List.of(), NOW).getFirst()
				.result()).isEqualTo(Result.WITHIN);
	}

	@Test
	void theDisconnectionLimitIsExtendableWithAJustification() {
		ReferenceRule disconnection = ReferenceRule.of(VERSION, IncidentType.DISCONNECTION, 5, 300, true, null, null,
				Priority.HIGH);
		Judgment recovery = ReferenceEvaluator.evaluate(episode(), Optional.of(disconnection), List.of(), NOW).get(1);
		assertThat(recovery.extendable()).isTrue();
		assertThat(recovery.limitMinutes()).isEqualTo(300);
	}

	// ----- Escenario 43: escalamiento de la Espera de conexion -----

	@Test
	void onlyATechnicalEscalationCountsAndASupervisionOneDoesNot() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 90);
		// (a) supervision a 10 min y tecnico a 70 min, T3 a 90 min.
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.SUPERVISION, 10, false),
				escalation(episode, TargetKind.TECHNICAL, 70, false)))).isEqualTo(Result.OUTSIDE);
	}

	@Test
	void withoutEscalationTheWaitUntilT3DecidesB() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 45);
		assertThat(escalationResult(episode, List.of())).isEqualTo(Result.WITHIN);
	}

	@Test
	void anOpenEpisodeWithoutEscalationIsMeasuredUntilTheCutOffC() {
		Episode episode = episode(); // abierto, 240 min hasta el corte
		assertThat(escalationResult(episode, List.of())).isEqualTo(Result.OUTSIDE);
	}

	@Test
	void anEscalationAfterTheWaitEndedDoesNotCountD() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 45);
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.TECHNICAL, 70, false))))
				.isEqualTo(Result.WITHIN);
	}

	@Test
	void aTimelyTechnicalEscalationIsWithinEvenIfRecoveryIsLateE() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 120);
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.TECHNICAL, 30, false))))
				.isEqualTo(Result.WITHIN);
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.TECHNICAL, 60, false))))
				.as("a exactamente 60 min aun esta dentro").isEqualTo(Result.WITHIN);
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.TECHNICAL, 61, false))))
				.isEqualTo(Result.OUTSIDE);
	}

	@Test
	void anAnnulledEscalationDoesNotCount() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 120);
		assertThat(escalationResult(episode, List.of(escalation(episode, TargetKind.TECHNICAL, 30, true))))
				.isEqualTo(Result.OUTSIDE);
	}

	@Test
	void anEscalationOfAnotherEpisodeDoesNotCount() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 120);
		Episode other = episode();
		assertThat(escalationResult(episode, List.of(escalation(other, TargetKind.TECHNICAL, 30, false))))
				.isEqualTo(Result.OUTSIDE);
	}

	@Test
	void theEarliestTechnicalEscalationIsTheOneThatCounts() {
		Episode episode = episode();
		record(episode, MilestoneKind.T3, 200);
		List<TicketEvent> events = new ArrayList<>(List.of(escalation(episode, TargetKind.TECHNICAL, 90, false),
				escalation(episode, TargetKind.TECHNICAL, 20, false)));
		assertThat(escalationResult(episode, events)).isEqualTo(Result.WITHIN);
	}

	// ----- KIA persistente -----

	@Test
	void anOutageIsPersistentOnlyAboveThirtyMinutesExactly() {
		ReferenceRule kia = ReferenceRule.of(VERSION, IncidentType.KIA_PERSISTENT_OUTAGE, null, null, false, null, 30,
				Priority.MEDIUM);
		assertThat(ReferenceEvaluator.isPersistentOutage(30 * 60, kia)).as("30 min exactos").isFalse();
		assertThat(ReferenceEvaluator.isPersistentOutage(30 * 60 + 1, kia)).as("30 min 1 s").isTrue();
		assertThat(ReferenceEvaluator.isPersistentOutage(29 * 60 + 59, kia)).isFalse();
		assertThat(ReferenceEvaluator.isPersistentOutage(3600, rule(5, 180, null))).as("sin umbral nunca").isFalse();
	}
}
