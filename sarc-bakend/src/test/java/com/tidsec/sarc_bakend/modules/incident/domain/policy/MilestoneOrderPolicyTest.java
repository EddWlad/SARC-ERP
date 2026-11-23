package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;

/** FR-043: reglas de orden de los hitos. */
class MilestoneOrderPolicyTest {

	private static final Instant NOW = Instant.parse("2026-11-22T15:00:00Z");
	private static final Instant T0 = NOW.minusSeconds(3600);

	private static Map<MilestoneKind, Instant> recorded(Object... pairs) {
		Map<MilestoneKind, Instant> map = new EnumMap<>(MilestoneKind.class);
		map.put(MilestoneKind.T0, T0);
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((MilestoneKind) pairs[i], (Instant) pairs[i + 1]);
		}
		return map;
	}

	private static void assertRejected(Runnable action, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class,
				error -> assertThat(error.code()).isEqualTo(code));
	}

	@Test
	void aMilestoneBeforeT0IsRejected() {
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T1, T0.minusSeconds(1), NOW, recorded(), false),
				IncidentErrors.MILESTONE_BEFORE_T0);
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T1, T0, NOW, recorded(), false)).isFalse();
	}

	@Test
	void aFutureMilestoneIsRejected() {
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T2, NOW.plusSeconds(1), NOW, recorded(), false),
				IncidentErrors.MILESTONE_IN_FUTURE);
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T2, NOW, NOW, recorded(), false)).isFalse();
	}

	@Test
	void t4BeforeARecordedMilestoneIsRejectedWithoutAnyConfirmation() {
		Map<MilestoneKind, Instant> others = recorded(MilestoneKind.T3, T0.plusSeconds(1800));
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T4, T0.plusSeconds(1000), NOW, others, true),
				IncidentErrors.MILESTONE_T4_BEFORE_MILESTONES);
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T4, T0.plusSeconds(1800), NOW, others, false)).isFalse();
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T4, T0.plusSeconds(2000), NOW, others, false)).isFalse();
	}

	@Test
	void anInOrderSequenceNeedsNoConfirmation() {
		Map<MilestoneKind, Instant> others = recorded(MilestoneKind.T1, T0.plusSeconds(300),
				MilestoneKind.T2, T0.plusSeconds(600));
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T3, T0.plusSeconds(900), NOW, others, false)).isFalse();
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T3, T0.plusSeconds(600), NOW, others, false))
				.as("T2 = T3 sigue en orden").isFalse();
	}

	@Test
	void anOutOfOrderSequenceNeedsTheConfirmationAndReportsIt() {
		Map<MilestoneKind, Instant> others = recorded(MilestoneKind.T2, T0.plusSeconds(600));
		// T1 después de T2.
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T1, T0.plusSeconds(900), NOW, others, false),
				IncidentErrors.MILESTONE_ORDER_UNCONFIRMED);
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T1, T0.plusSeconds(900), NOW, others, true))
				.as("con la confirmación se admite y queda marcado").isTrue();
	}

	@Test
	void t3BeforeT1IsAlsoOutOfOrder() {
		Map<MilestoneKind, Instant> others = recorded(MilestoneKind.T1, T0.plusSeconds(900));
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T3, T0.plusSeconds(300), NOW, others, false),
				IncidentErrors.MILESTONE_ORDER_UNCONFIRMED);
	}

	@Test
	void missingMilestonesDoNotCountAsDisorder() {
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T3, T0.plusSeconds(60), NOW, recorded(), false)).isFalse();
	}

	@Test
	void correctingT0CannotLeaveAMilestoneBeforeIt() {
		Map<MilestoneKind, Instant> others = recorded(MilestoneKind.T1, T0.plusSeconds(300));
		assertRejected(() -> MilestoneOrderPolicy.check(MilestoneKind.T0, T0.plusSeconds(600), NOW, others, false),
				IncidentErrors.MILESTONE_INVALID);
		assertThat(MilestoneOrderPolicy.check(MilestoneKind.T0, T0.minusSeconds(60), NOW, others, false)).isFalse();
	}
}
