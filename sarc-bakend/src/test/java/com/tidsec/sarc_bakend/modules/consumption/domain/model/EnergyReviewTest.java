package com.tidsec.sarc_bakend.modules.consumption.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReview;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergyReviewStatus;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionRevision;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

class EnergyReviewTest {
	private static final Instant NOW = Instant.parse("2026-10-21T15:00:00Z");
	private static final OperatingZone ZONE = new OperatingZone(ZoneId.of("America/Guayaquil"));
	private static final UUID BATCH = UUID.randomUUID();
	private static final EquipmentReference STATION = new EquipmentReference(UUID.randomUUID(),
			ChargingNetwork.JAC, InfrastructureType.CHARGING_STATION, "QA", "QA");

	private static SessionReading reading(String energy, boolean later) {
		return new SessionReading(NOW, later ? NOW.plusSeconds(600) : null,
				energy == null ? null : new BigDecimal(energy), "QA", "C1", null, null);
	}

	@Test
	void correctionsPreserveBothDecisionsOnlyWhileNormalizedEnergyDoesNotChange() {
		for (EnergyReviewStatus decision : new EnergyReviewStatus[] {EnergyReviewStatus.APPROVED, EnergyReviewStatus.REJECTED}) {
			EnergySession session = EnergySession.record(SessionSource.ENERLINK, "", "1", reading("1500", false),
					STATION, false, false, BATCH, ZONE);
			assertThat(session.getState()).isEqualTo(SessionState.ENERGY_REVIEW);
			EnergyReview review = session.decideEnergy(decision, " Confirmado ", UUID.randomUUID(), NOW);
			assertThat(review.getJustification()).isEqualTo("Confirmado");
			assertThat(review.getSessionRevision()).isEqualTo(1);
			SessionRevision previous = session.correct(reading("1500.0004", true), BATCH, ZONE, null, NOW);
			assertThat(previous.getEnergyReview()).isEqualTo(decision);
			assertThat(session.getEnergyReview()).isEqualTo(decision);
			session.correct(reading("1800", true), BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isEqualTo(EnergyReviewStatus.PENDING);
			session.correct(reading("1500", true), BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isEqualTo(EnergyReviewStatus.PENDING);
			session.correct(reading(null, false), BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isNull();
			assertThat(session.getState()).isEqualTo(SessionState.VALIDATED);
		}
	}

	@Test
	void approvalsWorkInEitherOrderAndTestAndLinkTakePrecedence() {
		for (boolean sourceFirst : new boolean[] {true, false}) {
			EnergySession session = EnergySession.record(SessionSource.WALLBOX, "", "2", reading("1500", false),
					null, false, false, BATCH, ZONE);
			assertThat(session.getState()).isEqualTo(SessionState.PENDING_LINK);
			session.link(STATION);
			assertThat(session.canApproveSource()).isTrue();
			if (sourceFirst) session.approveSource();
			session.decideEnergy(EnergyReviewStatus.APPROVED, "Verificada", null, NOW);
			if (!sourceFirst) {
				assertThat(session.getState()).isEqualTo(SessionState.AWAITING_APPROVAL);
				session.approveSource();
			}
			assertThat(session.getState()).isEqualTo(SessionState.VALIDATED);
			session.markTest(true);
			session.decideEnergy(EnergyReviewStatus.REJECTED, "Error confirmado", null, NOW);
			assertThat(session.getState()).isEqualTo(SessionState.TEST);
			session.markTest(false);
			assertThat(session.getState()).isEqualTo(SessionState.ENERGY_REJECTED);
		}
	}

	@Test
	void justificationAndDecisionAreEnforcedByTheDomain() {
		EnergySession session = EnergySession.record(SessionSource.ENERLINK, "", "3", reading("1500", false),
				STATION, false, false, BATCH, ZONE);
		assertThatThrownBy(() -> session.decideEnergy(EnergyReviewStatus.APPROVED, "   ", null, NOW))
				.extracting("code").isEqualTo("ENERGY_REVIEW_JUSTIFICATION_REQUIRED");
		assertThatThrownBy(() -> session.decideEnergy(EnergyReviewStatus.PENDING, "Texto", null, NOW))
				.extracting("code").isEqualTo("INVALID_ENERGY_REVIEW_DECISION");
		assertThatThrownBy(() -> session.decideEnergy(EnergyReviewStatus.APPROVED, "x".repeat(501), null, NOW))
				.extracting("code").isEqualTo("ENERGY_REVIEW_JUSTIFICATION_TOO_LONG");
		assertThat(session.getEnergyReview()).isEqualTo(EnergyReviewStatus.PENDING);
	}

	@Test
	void independentControlsSurviveTestLinkAndReassignment() {
		for (EnergyReviewStatus decision : EnergyReviewStatus.values()) {
			EnergySession session = EnergySession.record(SessionSource.WALLBOX, "", "4", reading("1500", false),
					null, false, true, BATCH, ZONE);
			if (decision != EnergyReviewStatus.PENDING) session.decideEnergy(decision, "Verificada", null, NOW);
			session.correct(reading("1500.0004", true), BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isEqualTo(decision);
			assertThat(session.getState()).isEqualTo(SessionState.TEST);
			session.markTest(false);
			assertThat(session.getState()).isEqualTo(SessionState.PENDING_LINK);
			session.markTest(true);
			session.link(STATION);
			session.approveSource();
			assertThat(session.getState()).isEqualTo(SessionState.TEST);
			session.markTest(false);
			assertThat(session.getState()).isEqualTo(switch (decision) {
				case PENDING -> SessionState.ENERGY_REVIEW;
				case APPROVED -> SessionState.VALIDATED;
				case REJECTED -> SessionState.ENERGY_REJECTED;
			});
			session.reassign(reading("1500", true), STATION, false, BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isEqualTo(decision);
			assertThat(session.getState()).isEqualTo(decision == EnergyReviewStatus.APPROVED
					? SessionState.AWAITING_APPROVAL : decision == EnergyReviewStatus.PENDING
					? SessionState.ENERGY_REVIEW : SessionState.ENERGY_REJECTED);
			session.correct(reading("1000", true), BATCH, ZONE, null, NOW);
			assertThat(session.getEnergyReview()).isNull();
		}
	}
}
