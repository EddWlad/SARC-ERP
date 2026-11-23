package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.shared.error.ConflictException;
import com.tidsec.sarc_bakend.shared.error.SarcException;

/** FR-041, FR-042, FR-036: registrar y corregir hitos con revisiones de solo anexion. */
class MilestoneRegistrationTest {

	private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
	private static final Instant T0 = NOW.minusSeconds(7200);
	private static final UUID ACTOR = UUID.randomUUID();

	private static Episode episode() {
		Ticket ticket = Ticket.open(new NewTicket(9001, UUID.randomUUID(), AffectedElement.chatbot(null),
				IncidentType.CHATBOT_SERVICE_FAILURE, ReportOrigin.PLATFORM, null, "x", Priority.MEDIUM, ACTOR, "Ana",
				"CHATBOT", null, T0, "Aviso por telefono", UUID.randomUUID(), ACTOR, NOW));
		return ticket.latestEpisode();
	}

	private static void assertCode(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(SarcException.class,
				error -> assertThat(error.code()).isEqualTo(code));
	}

	@Test
	void recordsAPendingMilestoneAtTheCurrentTimeAndAppendsItsRevision() {
		Episode episode = episode();
		MilestoneRevision revision = episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, null, null,
				null, false, ACTOR, NOW);

		Milestone t1 = episode.milestone(MilestoneKind.T1);
		assertThat(t1.getStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(t1.getOccurredAt()).isEqualTo(NOW);
		assertThat(t1.isRetroactive()).isFalse();
		assertThat(t1.getRecordedBy()).isEqualTo(ACTOR);
		assertThat(revision.getPreviousStatus()).isEqualTo(MilestoneStatus.PENDING);
		assertThat(revision.getNewStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(revision.getNewOccurredAt()).isEqualTo(NOW);
		assertThat(revision.getJustification()).isNull();
	}

	@Test
	void anEarlierTimeIsRetroactiveAndNeedsAReason() {
		Episode episode = episode();
		Instant earlier = NOW.minusSeconds(1800);
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, earlier, null, null, false,
				ACTOR, NOW), IncidentErrors.MILESTONE_REASON_REQUIRED);
		assertThat(episode.milestone(MilestoneKind.T1).getStatus()).as("no cambia nada si se rechaza")
				.isEqualTo(MilestoneStatus.PENDING);

		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, earlier, "La plataforma lo mostro antes", null,
				false, ACTOR, NOW);
		Milestone t1 = episode.milestone(MilestoneKind.T1);
		assertThat(t1.isRetroactive()).isTrue();
		assertThat(t1.getReason()).isEqualTo("La plataforma lo mostro antes");
	}

	@Test
	void aTimeWithinTheToleranceIsNotRetroactive() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, NOW.minusSeconds(40), null, null, false,
				ACTOR, NOW);
		assertThat(episode.milestone(MilestoneKind.T1).isRetroactive()).isFalse();
	}

	@Test
	void notApplicableAndNoDataNeedAReasonAndHaveNoTime() {
		Episode episode = episode();
		for (MilestoneStatus status : new MilestoneStatus[] { MilestoneStatus.NOT_APPLICABLE, MilestoneStatus.NO_DATA }) {
			assertCode(() -> episode.recordMilestone(MilestoneKind.T2, status, null, " ", null, false, ACTOR, NOW),
					IncidentErrors.MILESTONE_REASON_REQUIRED);
		}
		episode.recordMilestone(MilestoneKind.T2, MilestoneStatus.NOT_APPLICABLE, null, "No hubo apoyo", null, false,
				ACTOR, NOW);
		episode.recordMilestone(MilestoneKind.T3, MilestoneStatus.NO_DATA, NOW, "Se desconoce la hora", null, false,
				ACTOR, NOW);
		assertThat(episode.milestone(MilestoneKind.T2).getOccurredAt()).isNull();
		assertThat(episode.milestone(MilestoneKind.T3).getOccurredAt()).as("sin dato no tiene hora").isNull();
		assertThat(episode.milestone(MilestoneKind.T3).getStatus()).isEqualTo(MilestoneStatus.NO_DATA);
	}

	@Test
	void t4NeverAdmitsNotApplicableOrNoDataAndIsOnlyRecordedWhenClosing() {
		Episode episode = episode();
		assertCode(() -> episode.correctMilestone(MilestoneKind.T4, MilestoneStatus.NOT_APPLICABLE, null, "x", "y", false,
				ACTOR, NOW), IncidentErrors.MILESTONE_T4_NOT_APPLICABLE);
		assertCode(() -> episode.correctMilestone(MilestoneKind.T4, MilestoneStatus.NO_DATA, null, "x", "y", false, ACTOR,
				NOW), IncidentErrors.MILESTONE_T4_NOT_APPLICABLE);
		assertCode(() -> episode.recordMilestone(MilestoneKind.T4, MilestoneStatus.RECORDED, null, null, null, false,
				ACTOR, NOW), IncidentErrors.EPISODE_NOT_EDITABLE);
	}

	@Test
	void aMilestoneNeverGoesBackToPendingAndT0AlwaysHasATime() {
		Episode episode = episode();
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.PENDING, null, null, null, false,
				ACTOR, NOW), IncidentErrors.MILESTONE_INVALID);
		assertCode(() -> episode.correctMilestone(MilestoneKind.T0, MilestoneStatus.NO_DATA, null, "x", "y", false, ACTOR,
				NOW), IncidentErrors.MILESTONE_INVALID);
	}

	@Test
	void aCorrectionNeedsAJustificationKeepsThePreviousValueAndAppendsARevision() {
		Episode episode = episode();
		Instant first = NOW.minusSeconds(60);
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, first, null, null, false, ACTOR, NOW);

		Instant corrected = NOW.minusSeconds(1500);
		assertCode(() -> episode.correctMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, corrected, "Hora real", " ",
				false, ACTOR, NOW), IncidentErrors.MILESTONE_REASON_REQUIRED);

		MilestoneRevision revision = episode.correctMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, corrected,
				"Hora real segun la plataforma", "La hora anterior estaba mal", false, ACTOR, NOW);
		assertThat(episode.milestone(MilestoneKind.T1).getOccurredAt()).isEqualTo(corrected);
		assertThat(revision.getPreviousStatus()).isEqualTo(MilestoneStatus.RECORDED);
		assertThat(revision.getPreviousOccurredAt()).as("conserva el valor anterior").isEqualTo(first);
		assertThat(revision.getNewOccurredAt()).isEqualTo(corrected);
		assertThat(revision.getJustification()).isEqualTo("La hora anterior estaba mal");
	}

	@Test
	void aMilestoneThatAlreadyHasValueCannotBeRecordedAgainWithoutCorrecting() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, null, null, null, false, ACTOR, NOW);
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, null, null, null, false,
				ACTOR, NOW), IncidentErrors.MILESTONE_INVALID);
	}

	@Test
	void anOutOfOrderSequenceIsStoredWithItsConfirmation() {
		Episode episode = episode();
		episode.recordMilestone(MilestoneKind.T2, MilestoneStatus.RECORDED, NOW.minusSeconds(600), "Retroactivo", null,
				false, ACTOR, NOW);
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, NOW.minusSeconds(120),
				"Retroactivo", null, false, ACTOR, NOW), IncidentErrors.MILESTONE_ORDER_UNCONFIRMED);
		episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, NOW.minusSeconds(120), "Retroactivo", null, true,
				ACTOR, NOW);
		assertThat(episode.milestone(MilestoneKind.T1).isOrderWarningAck()).isTrue();
		assertThat(episode.milestone(MilestoneKind.T2).isOrderWarningAck()).isFalse();
	}

	@Test
	void aTimeBeforeT0OrInTheFutureIsRejected() {
		Episode episode = episode();
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, T0.minusSeconds(60), "x",
				null, false, ACTOR, NOW), IncidentErrors.MILESTONE_BEFORE_T0);
		assertCode(() -> episode.recordMilestone(MilestoneKind.T1, MilestoneStatus.RECORDED, NOW.plusSeconds(60), null,
				null, false, ACTOR, NOW), IncidentErrors.MILESTONE_IN_FUTURE);
	}

	@Test
	void theErrorCodesAreTheContractOnes() {
		assertThat(IncidentErrors.episodeNotEditable("x")).isInstanceOf(ConflictException.class);
	}
}
