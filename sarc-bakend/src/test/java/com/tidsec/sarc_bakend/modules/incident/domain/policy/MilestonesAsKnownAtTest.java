package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.AffectedElement;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.NewTicket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;

/** FR-074a, FR-086, SC-012: aplicar las revisiones registradas hasta X reproduce lo que se sabia en X. */
class MilestonesAsKnownAtTest {

	private static final Instant CREATED = Instant.parse("2026-11-01T10:00:00Z");
	private static final Instant T0 = CREATED.minusSeconds(600);
	private static final UUID ACTOR = UUID.randomUUID();

	private final List<MilestoneRevision> revisions = new ArrayList<>();

	private static Episode episode() {
		return Ticket.open(new NewTicket(9001, UUID.randomUUID(), AffectedElement.chatbot(null),
				IncidentType.CHATBOT_SERVICE_FAILURE, ReportOrigin.PLATFORM, null, "x", Priority.MEDIUM, ACTOR, "Ana",
				"CHATBOT", null, T0, "Aviso", UUID.randomUUID(), ACTOR, CREATED)).latestEpisode();
	}

	private Instant at(long minutes) {
		return CREATED.plusSeconds(minutes * 60);
	}

	private void record(Episode episode, MilestoneKind kind, Instant occurredAt, Instant recordedAt) {
		revisions.add(episode.recordMilestone(kind, MilestoneStatus.RECORDED, occurredAt, "Retroactivo", null, true, ACTOR,
				recordedAt));
	}

	private void correct(Episode episode, MilestoneKind kind, Instant occurredAt, Instant recordedAt) {
		revisions.add(episode.correctMilestone(kind, MilestoneStatus.RECORDED, occurredAt, "Retroactivo", "Error de captura",
				true, ACTOR, recordedAt));
	}

	@Test
	void withoutRevisionsTheCurrentValuesAreTheKnownOnes() {
		Episode episode = episode();

		Map<MilestoneKind, Value> known = MilestonesAsKnownAt.of(episode, revisions, at(5));

		assertThat(known.get(MilestoneKind.T0)).isEqualTo(new Value(MilestoneStatus.RECORDED, T0));
		assertThat(known.get(MilestoneKind.T4)).isEqualTo(new Value(MilestoneStatus.PENDING, null));
	}

	@Test
	void aMilestoneRecordedLaterIsStillPendingBeforeItsRegistration() {
		Episode episode = episode();
		record(episode, MilestoneKind.T1, at(3), at(20));

		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(10)).get(MilestoneKind.T1))
				.isEqualTo(new Value(MilestoneStatus.PENDING, null));
		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(20)).get(MilestoneKind.T1))
				.as("el instante de registro cuenta (<= X)").isEqualTo(new Value(MilestoneStatus.RECORDED, at(3)));
	}

	@Test
	void aLaterCorrectionDoesNotChangeWhatWasKnownBeforeIt() {
		Episode episode = episode();
		record(episode, MilestoneKind.T1, at(3), at(4));
		correct(episode, MilestoneKind.T1, at(8), at(60));

		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(30)).get(MilestoneKind.T1))
				.as("antes de corregir se conocia el valor original").isEqualTo(new Value(MilestoneStatus.RECORDED, at(3)));
		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(61)).get(MilestoneKind.T1))
				.isEqualTo(new Value(MilestoneStatus.RECORDED, at(8)));
		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(2)).get(MilestoneKind.T1))
				.as("antes del primer registro: pendiente").isEqualTo(new Value(MilestoneStatus.PENDING, null));
	}

	@Test
	void aCorrectedT0IsKnownWithItsOriginalValueBeforeTheCorrection() {
		Episode episode = episode();
		Instant correctedT0 = T0.minusSeconds(3600);
		correct(episode, MilestoneKind.T0, correctedT0, at(90));

		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(30)).get(MilestoneKind.T0))
				.isEqualTo(new Value(MilestoneStatus.RECORDED, T0));
		assertThat(MilestonesAsKnownAt.of(episode, revisions, at(90)).get(MilestoneKind.T0))
				.isEqualTo(new Value(MilestoneStatus.RECORDED, correctedT0));
	}

	@Test
	void revisionsOfOtherEpisodesOrOtherMilestonesAreIgnored() {
		Episode episode = episode();
		Episode other = episode();
		record(other, MilestoneKind.T1, at(3), at(4));
		record(episode, MilestoneKind.T2, at(6), at(7));

		Map<MilestoneKind, Value> known = MilestonesAsKnownAt.of(episode, revisions, at(100));

		assertThat(known.get(MilestoneKind.T1)).isEqualTo(new Value(MilestoneStatus.PENDING, null));
		assertThat(known.get(MilestoneKind.T2)).isEqualTo(new Value(MilestoneStatus.RECORDED, at(6)));
	}
}
