package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.shared.error.SarcException;

/** Transiciones de una visita tecnica y los datos que exige cancelarla o aplazarla (FR-035, Protocolo 9.4). */
class VisitTransitionsTest {

	private static Visit visit() {
		return Visit.request(UUID.randomUUID(), UUID.randomUUID(), "Proveedor Uno");
	}

	private static void postpone(Visit visit) {
		visit.moveTo(VisitStage.POSTPONED, null, "Supervisión", "Correo del proveedor", "Si no hay fecha en 48 h");
	}

	@Test
	void aNewVisitIsRequestedAndFollowsTheNormalPath() {
		Visit visit = visit();
		assertThat(visit.getStage()).isEqualTo(VisitStage.REQUESTED);
		for (VisitStage next : List.of(VisitStage.ASSIGNED, VisitStage.CONFIRMED, VisitStage.EXECUTED,
				VisitStage.REPORT_RECEIVED)) {
			visit.moveTo(next, null, null, null, null);
			assertThat(visit.getStage()).isEqualTo(next);
		}
	}

	@Test
	void stepsCannotBeSkippedOrGoBackwards() {
		Visit visit = visit();
		assertInvalid(() -> visit.moveTo(VisitStage.CONFIRMED, null, null, null, null));
		assertInvalid(() -> visit.moveTo(VisitStage.EXECUTED, null, null, null, null));
		visit.moveTo(VisitStage.ASSIGNED, null, null, null, null);
		visit.moveTo(VisitStage.CONFIRMED, null, null, null, null);
		assertInvalid(() -> visit.moveTo(VisitStage.REQUESTED, null, null, null, null));
		assertInvalid(() -> visit.moveTo(VisitStage.ASSIGNED, null, null, null, null));
		assertThat(visit.getStage()).isEqualTo(VisitStage.CONFIRMED);
	}

	@Test
	void cancellingOrPostponingNeedsWhoDecidedTheEvidenceAndTheReescalationCondition() {
		for (VisitStage stage : List.of(VisitStage.CANCELLED, VisitStage.POSTPONED)) {
			assertInvalid(() -> visit().moveTo(stage, null, null, null, null));
			assertInvalid(() -> visit().moveTo(stage, null, "Supervisión", "Correo", null));
			assertInvalid(() -> visit().moveTo(stage, null, "Supervisión", " ", "Si no responde"));
			assertInvalid(() -> visit().moveTo(stage, null, "  ", "Correo", "Si no responde"));
		}
		Visit cancelled = visit();
		cancelled.moveTo(VisitStage.CANCELLED, null, "Supervisión", "Correo del proveedor", "Si reaparece la falla");
		assertThat(cancelled.getStage()).isEqualTo(VisitStage.CANCELLED);
		assertThat(cancelled.getCancelDecidedBy()).isEqualTo("Supervisión");
		assertThat(cancelled.getCancelEvidenceNote()).isEqualTo("Correo del proveedor");
		assertThat(cancelled.getReescalationCondition()).isEqualTo("Si reaparece la falla");
	}

	@Test
	void aRejectedTransitionLeavesTheVisitUntouched() {
		Visit visit = visit();
		assertInvalid(() -> visit.moveTo(VisitStage.CANCELLED, "Otro Proveedor", null, null, null));
		assertThat(visit.getStage()).isEqualTo(VisitStage.REQUESTED);
		assertThat(visit.getProviderName()).isEqualTo("Proveedor Uno");
		assertThat(visit.getCancelDecidedBy()).isNull();
	}

	@Test
	void aPostponedVisitCanBeAssignedOrConfirmedAgainButACancelledOneIsFinal() {
		Visit again = visit();
		postpone(again);
		again.moveTo(VisitStage.ASSIGNED, null, null, null, null);
		assertThat(again.getStage()).isEqualTo(VisitStage.ASSIGNED);

		Visit confirmed = visit();
		postpone(confirmed);
		confirmed.moveTo(VisitStage.CONFIRMED, null, null, null, null);
		assertThat(confirmed.getStage()).isEqualTo(VisitStage.CONFIRMED);

		Visit postponed = visit();
		postpone(postponed);
		assertInvalid(() -> postponed.moveTo(VisitStage.EXECUTED, null, null, null, null));
		assertInvalid(() -> postponed.moveTo(VisitStage.CANCELLED, null, "Supervisión", "Correo", "Condición"));

		Visit cancelled = visit();
		cancelled.moveTo(VisitStage.CANCELLED, null, "Supervisión", "Correo", "Condición");
		for (VisitStage any : VisitStage.values()) {
			assertInvalid(() -> cancelled.moveTo(any, null, "Supervisión", "Correo", "Condición"));
		}
	}

	@Test
	void anExecutedVisitCanOnlyReceiveItsReport() {
		Visit visit = visit();
		for (VisitStage next : List.of(VisitStage.ASSIGNED, VisitStage.CONFIRMED, VisitStage.EXECUTED)) {
			visit.moveTo(next, null, null, null, null);
		}
		assertInvalid(() -> visit.moveTo(VisitStage.CANCELLED, null, "Supervisión", "Correo", "Condición"));
		assertInvalid(() -> visit.moveTo(VisitStage.POSTPONED, null, "Supervisión", "Correo", "Condición"));
		visit.moveTo(VisitStage.REPORT_RECEIVED, null, null, null, null);
		assertThat(VisitStage.REPORT_RECEIVED.next()).isEmpty();
	}

	@Test
	void theProviderOnlyChangesWhenOneArrives() {
		Visit visit = visit();
		visit.moveTo(VisitStage.ASSIGNED, " ", null, null, null);
		assertThat(visit.getProviderName()).isEqualTo("Proveedor Uno");
		visit.moveTo(VisitStage.CONFIRMED, " Proveedor Dos ", null, null, null);
		assertThat(visit.getProviderName()).isEqualTo("Proveedor Dos");
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(SarcException.class,
				ex -> assertThat(ex.code()).isEqualTo(IncidentErrors.VISIT_TRANSITION_INVALID));
	}
}
