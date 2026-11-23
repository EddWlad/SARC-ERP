package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.EpisodeFact;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.Kpis;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.TimeMeasure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.VisitFact;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Judgment;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Measure;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Result;

/** FR-050, FR-080, FR-084, SC-004: promedios con su denominador, exclusiones aparte, repartos y repetidores. */
class IncidentKpiCalculatorTest {

	private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
	private static final Instant T0 = Instant.parse("2026-11-10T15:00:00Z");

	/** Un hito registrado, con T0 más {@code minutes}. */
	private static Value at(long minutes) {
		return new Value(MilestoneStatus.RECORDED, T0.plusSeconds(minutes * 60));
	}

	private static Value status(MilestoneStatus status) {
		return new Value(status, null);
	}

	private static Map<MilestoneKind, Value> milestones(Value t1, Value t2, Value t3, Value t4) {
		Map<MilestoneKind, Value> map = new EnumMap<>(MilestoneKind.class);
		map.put(MilestoneKind.T0, new Value(MilestoneStatus.RECORDED, T0));
		map.put(MilestoneKind.T1, t1);
		map.put(MilestoneKind.T2, t2);
		map.put(MilestoneKind.T3, t3);
		map.put(MilestoneKind.T4, t4);
		return map;
	}

	private static final Value PENDING = status(MilestoneStatus.PENDING);

	private static EpisodeFact fact(String code, int ordinal, Map<MilestoneKind, Value> milestones, ClosureResult closure,
			List<Judgment> judgments, IncidentType type, String network, UUID equipment, Priority priority) {
		boolean open = closure == null;
		return new EpisodeFact(UUID.nameUUIDFromBytes(code.getBytes()), code, ordinal, open, closure, type,
				network == null ? ElementClass.CHATBOT_SERVICE : ElementClass.EQUIPMENT, network, equipment,
				equipment == null ? null : "Equipo " + equipment.toString().substring(0, 4), priority,
				ReportOrigin.PLATFORM, T0, milestones, open ? 30L : null, judgments, null);
	}

	private static EpisodeFact simple(String code, Map<MilestoneKind, Value> milestones, ClosureResult closure) {
		return fact(code, 1, milestones, closure, List.of(), IncidentType.DISCONNECTION, "KIA", UUID.randomUUID(),
				Priority.HIGH);
	}

	private static TimeMeasure measure(Kpis kpis, String pair) {
		return kpis.measures().stream().filter(item -> item.pair().equals(pair)).findFirst().orElseThrow();
	}

	@Test
	void everyAverageCarriesItsDenominatorAndWhoDoesNotEnter() {
		List<EpisodeFact> facts = List.of(
				simple("A", milestones(at(4), at(10), at(60), at(90)), ClosureResult.RECOVERED),
				simple("B", milestones(at(6), status(MilestoneStatus.NOT_APPLICABLE), at(120), at(150)),
						ClosureResult.RECOVERED),
				simple("C", milestones(status(MilestoneStatus.NO_DATA), at(20), at(70), at(100)), ClosureResult.RECOVERED),
				simple("D", milestones(at(3), PENDING, PENDING, PENDING), null));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		TimeMeasure validation = measure(kpis, "T1-T0");
		assertThat(validation.n()).isEqualTo(3);
		assertThat(validation.averageMinutes()).isEqualByComparingTo(new BigDecimal("4.3"));
		assertThat(validation.noData()).isEqualTo(1);
		TimeMeasure action = measure(kpis, "T2-T0");
		assertThat(action.n()).as("A y C").isEqualTo(2);
		assertThat(action.notApplicable()).isEqualTo(1);
		assertThat(action.open()).as("el episodio abierto todavia no tiene T2").isEqualTo(1);
		assertThat(measure(kpis, "T3-T0").averageMinutes()).isEqualByComparingTo(new BigDecimal("83.3"));
		TimeMeasure closeToRecover = measure(kpis, "T4-T3");
		assertThat(closeToRecover.n()).isEqualTo(3);
		assertThat(closeToRecover.averageMinutes()).isEqualByComparingTo(new BigDecimal("30.0"));
	}

	@Test
	void aMeasureWithNoEpisodesHasNoAverageNeverZero() {
		Kpis kpis = IncidentKpiCalculator.compute(List.of(simple("A", milestones(PENDING, PENDING, PENDING, PENDING), null)),
				List.of(), ZONE);

		assertThat(measure(kpis, "T1-T0").averageMinutes()).isNull();
		assertThat(measure(kpis, "T1-T0").n()).isZero();
		assertThat(measure(kpis, "T1-T0").open()).isEqualTo(1);
	}

	@Test
	void duplicatesAndRegistrationErrorsAreReportedApartAndStayOutOfEverythingElse() {
		List<EpisodeFact> facts = List.of(
				simple("A", milestones(at(4), at(10), at(60), at(90)), ClosureResult.RECOVERED),
				simple("B", milestones(at(1), at(1), at(1), at(1)), ClosureResult.DUPLICATE),
				simple("C", milestones(at(2), at(2), at(2), at(2)), ClosureResult.REGISTERED_IN_ERROR));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		assertThat(kpis.summary().excluded()).isEqualTo(2);
		assertThat(kpis.summary().episodes()).isEqualTo(3);
		assertThat(kpis.summary().closedEpisodes()).isEqualTo(1);
		assertThat(measure(kpis, "T1-T0").n()).isEqualTo(1);
		assertThat(kpis.byType().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1);
	}

	@Test
	void theValidationPercentageUsesOnlyEpisodesWithThatReferenceAndAT1() {
		Judgment within = new Judgment(Measure.VALIDATION, 5, false, Result.WITHIN);
		Judgment outside = new Judgment(Measure.VALIDATION, 5, false, Result.OUTSIDE);
		Judgment notEvaluable = new Judgment(Measure.VALIDATION, 5, false, Result.NOT_EVALUABLE);
		List<EpisodeFact> facts = List.of(
				fact("A", 1, milestones(at(4), PENDING, PENDING, PENDING), null, List.of(within),
						IncidentType.DISCONNECTION, "KIA", UUID.randomUUID(), Priority.HIGH),
				fact("B", 1, milestones(at(9), PENDING, PENDING, PENDING), null, List.of(outside),
						IncidentType.DISCONNECTION, "KIA", UUID.randomUUID(), Priority.HIGH),
				fact("C", 1, milestones(PENDING, PENDING, PENDING, PENDING), null, List.of(notEvaluable),
						IncidentType.DISCONNECTION, "KIA", UUID.randomUUID(), Priority.HIGH),
				simple("D", milestones(at(1), PENDING, PENDING, PENDING), null));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		assertThat(kpis.validationN()).isEqualTo(2);
		assertThat(kpis.validationWithin()).isEqualTo(1);
		assertThat(kpis.validationPercent()).isEqualByComparingTo(new BigDecimal("50.0"));
	}

	@Test
	void referenceCountsByMeasureAndTypeAndTheDeviationsWithTheirJustification() {
		Judgment outside = new Judgment(Measure.RECOVERY, 180, true, Result.OUTSIDE);
		Judgment within = new Judgment(Measure.RECOVERY, 180, true, Result.WITHIN);
		EpisodeFact justified = new EpisodeFact(UUID.randomUUID(), "TICKET-9001", 1, false, ClosureResult.RECOVERED,
				IncidentType.DISCONNECTION, ElementClass.EQUIPMENT, "KIA", UUID.randomUUID(), "Equipo", Priority.HIGH,
				ReportOrigin.PLATFORM, T0, milestones(at(4), at(10), at(200), at(210)), null, List.of(outside),
				"Falla del proveedor");
		List<EpisodeFact> facts = List.of(justified,
				fact("B", 1, milestones(at(4), at(10), at(60), at(70)), ClosureResult.RECOVERED, List.of(within),
						IncidentType.DISCONNECTION, "KIA", UUID.randomUUID(), Priority.HIGH),
				fact("C", 1, milestones(at(4), at(10), at(60), at(70)), ClosureResult.RECOVERED, List.of(),
						IncidentType.CHATBOT_SERVICE_FAILURE, null, null, Priority.MEDIUM));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		assertThat(kpis.references()).singleElement().satisfies(count -> {
			assertThat(count.within()).isEqualTo(1);
			assertThat(count.outside()).isEqualTo(1);
			assertThat(count.notEvaluable()).isZero();
		});
		assertThat(kpis.deviations()).singleElement().satisfies(deviation -> {
			assertThat(deviation.code()).isEqualTo("TICKET-9001");
			assertThat(deviation.justification()).isEqualTo("Falla del proveedor");
		});
		assertThat(kpis.withoutReference()).containsExactly(IncidentType.CHATBOT_SERVICE_FAILURE);
	}

	@Test
	void theDistributionsCountEpisodesWithSinRedForNonChargers() {
		UUID equipment = UUID.randomUUID();
		List<EpisodeFact> facts = List.of(
				fact("A", 1, milestones(at(1), PENDING, PENDING, PENDING), null, List.of(), IncidentType.DISCONNECTION,
						"KIA", equipment, Priority.CRITICAL),
				fact("B", 1, milestones(at(1), PENDING, PENDING, PENDING), null, List.of(), IncidentType.DISCONNECTION,
						"KIA", equipment, Priority.HIGH),
				fact("C", 1, milestones(at(1), PENDING, PENDING, PENDING), null, List.of(),
						IncidentType.CHATBOT_SERVICE_FAILURE, null, null, Priority.MEDIUM));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		assertThat(kpis.byNetwork()).containsExactly(Map.entry("KIA", 2), Map.entry("Sin red", 1));
		assertThat(kpis.byType()).containsEntry("DISCONNECTION", 2).containsEntry("CHATBOT_SERVICE_FAILURE", 1);
		assertThat(kpis.byClass()).containsEntry("EQUIPMENT", 2).containsEntry("CHATBOT_SERVICE", 1);
		assertThat(kpis.byEquipment()).containsEntry("Sin equipo", 1);
		assertThat(kpis.byPriority()).containsEntry("CRITICAL", 1);
		assertThat(kpis.byOrigin()).containsEntry("PLATFORM", 3);
		assertThat(kpis.byDay()).containsEntry("2026-11-10", 3);
		assertThat(kpis.summary().critical()).isEqualTo(1);
	}

	@Test
	void reopeningsAndTheRepeatersByEpisodeCountWithoutALabel() {
		UUID repeater = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		List<EpisodeFact> facts = List.of(
				fact("A", 1, milestones(at(1), at(2), at(3), at(4)), ClosureResult.RECOVERED, List.of(),
						IncidentType.DISCONNECTION, "KIA", repeater, Priority.HIGH),
				fact("A", 2, milestones(at(1), PENDING, PENDING, PENDING), null, List.of(), IncidentType.DISCONNECTION,
						"KIA", repeater, Priority.HIGH),
				fact("B", 1, milestones(at(1), PENDING, PENDING, PENDING), null, List.of(), IncidentType.DISCONNECTION,
						"KIA", other, Priority.HIGH));

		Kpis kpis = IncidentKpiCalculator.compute(facts, List.of(), ZONE);

		assertThat(kpis.summary().reopened()).isEqualTo(1);
		assertThat(kpis.summary().uniqueTickets()).isEqualTo(2);
		assertThat(kpis.summary().episodes()).isEqualTo(3);
		assertThat(kpis.repeaters()).hasSize(2);
		assertThat(kpis.repeaters().get(0).episodes()).isEqualTo(2);
		assertThat(kpis.repeaters().get(0).toString()).doesNotContainIgnoringCase("recurrente");
	}

	@Test
	void visitsCountByReachedStage() {
		UUID episode = UUID.randomUUID();
		List<VisitFact> visits = List.of(
				new VisitFact(episode, IncidentKpiCalculator.reached(List.of(VisitStage.ASSIGNED, VisitStage.CONFIRMED, VisitStage.EXECUTED))),
				new VisitFact(episode, IncidentKpiCalculator.reached(List.of(VisitStage.POSTPONED, VisitStage.ASSIGNED, VisitStage.CONFIRMED))),
				new VisitFact(episode, IncidentKpiCalculator.reached(List.of(VisitStage.CANCELLED))),
				new VisitFact(episode, Set.of(VisitStage.REQUESTED)));

		Kpis kpis = IncidentKpiCalculator.compute(List.of(), visits, ZONE);

		assertThat(kpis.visits().requested()).isEqualTo(4);
		assertThat(kpis.visits().confirmed()).isEqualTo(2);
		assertThat(kpis.visits().cancelledOrPostponed()).isEqualTo(2);
		assertThat(kpis.visits().executed()).isEqualTo(1);
	}

	@Test
	void anEmptyPeriodYieldsZerosAndNoAverages() {
		Kpis kpis = IncidentKpiCalculator.compute(List.of(), List.of(), ZONE);

		assertThat(kpis.summary().episodes()).isZero();
		assertThat(kpis.measures()).allSatisfy(item -> assertThat(item.averageMinutes()).isNull());
		assertThat(kpis.validationPercent()).isNull();
		assertThat(kpis.repeaters()).isEmpty();
	}
}
