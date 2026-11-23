package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CauseKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureCheck;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ClosureRequirements.Input;

/** Requisitos de cierre por clase de elemento (FR-047, FR-048, FR-048a), con la lista completa de lo que falta. */
class ClosureRequirementsTest {

	private static final Instant T4 = Instant.parse("2026-11-21T20:00:00Z");

	private static final Map<String, ClosureCheck> ALL_CHECKS = Map.of("platformNormal", ClosureCheck.CONFIRMED,
			"stabilityChecked", ClosureCheck.NOT_APPLICABLE, "noAffectedUser", ClosureCheck.CONFIRMED, "postTest",
			ClosureCheck.NOT_APPLICABLE);

	private static Map<MilestoneKind, MilestoneStatus> statuses(MilestoneStatus t1, MilestoneStatus t2, MilestoneStatus t3) {
		Map<MilestoneKind, MilestoneStatus> map = new EnumMap<>(MilestoneKind.class);
		map.put(MilestoneKind.T1, t1);
		map.put(MilestoneKind.T2, t2);
		map.put(MilestoneKind.T3, t3);
		return map;
	}

	private static final Map<MilestoneKind, MilestoneStatus> RESOLVED = statuses(MilestoneStatus.RECORDED,
			MilestoneStatus.NOT_APPLICABLE, MilestoneStatus.NO_DATA);

	/** Un cierre completo de la clase indicada; cada prueba le quita algo. */
	private static Input complete(ElementClass elementClass) {
		return new Input(elementClass, T4, RESOLVED, ClosureResult.RECOVERED, null, CauseKind.CONFIRMED,
				"Falla del enlace", elementClass == ElementClass.EQUIPMENT ? ALL_CHECKS : Map.of(), true, null, true, true,
				null, false, null);
	}

	private static Input with(Input base, java.util.function.UnaryOperator<InputBuilder> change) {
		return change.apply(new InputBuilder(base)).build();
	}

	/** Copia modificable de un {@link Input}. */
	private static final class InputBuilder {
		ElementClass elementClass;
		Instant t4;
		Map<MilestoneKind, MilestoneStatus> statuses;
		ClosureResult result;
		UUID duplicateOf;
		CauseKind causeKind;
		String causeText;
		Map<String, ClosureCheck> checks;
		boolean finalEvidence;
		String finalWaiver;
		boolean episodeEvidence;
		boolean communication;
		String communicationWaiver;
		boolean outside;
		String deviation;

		InputBuilder(Input in) {
			elementClass = in.elementClass();
			t4 = in.t4();
			statuses = in.statuses();
			result = in.result();
			duplicateOf = in.duplicateOfTicketId();
			causeKind = in.causeKind();
			causeText = in.causeText();
			checks = in.checks();
			finalEvidence = in.hasFinalEvidence();
			finalWaiver = in.finalEvidenceWaiver();
			episodeEvidence = in.hasEpisodeEvidence();
			communication = in.hasClosureCommunication();
			communicationWaiver = in.closureCommunicationWaiver();
			outside = in.outsideReference();
			deviation = in.deviationJustification();
		}

		Input build() {
			return new Input(elementClass, t4, statuses, result, duplicateOf, causeKind, causeText, checks, finalEvidence,
					finalWaiver, episodeEvidence, communication, communicationWaiver, outside, deviation);
		}
	}

	@Test
	void aCompleteClosureOfEachClassHasNothingMissing() {
		for (ElementClass elementClass : ElementClass.values()) {
			assertThat(ClosureRequirements.missing(complete(elementClass))).as(elementClass.name()).isEmpty();
		}
	}

	@Test
	void anEquipmentNeedsT4AndT1ToT3InAnExplicitState() {
		Input none = with(complete(ElementClass.EQUIPMENT), b -> {
			b.t4 = null;
			b.statuses = statuses(MilestoneStatus.PENDING, MilestoneStatus.RECORDED, MilestoneStatus.PENDING);
			return b;
		});
		assertThat(ClosureRequirements.missing(none)).containsExactly("T4", "T1", "T3");
	}

	@Test
	void everyStateOfT1ToT3ExceptPendingIsExplicit() {
		for (MilestoneStatus status : List.of(MilestoneStatus.RECORDED, MilestoneStatus.NOT_APPLICABLE,
				MilestoneStatus.NO_DATA)) {
			Input input = with(complete(ElementClass.EQUIPMENT), b -> {
				b.statuses = statuses(status, status, status);
				return b;
			});
			assertThat(ClosureRequirements.missing(input)).as(status.name()).isEmpty();
		}
	}

	@Test
	void theResultAndTheCauseAreRequired() {
		Input input = with(complete(ElementClass.EQUIPMENT), b -> {
			b.result = null;
			b.causeKind = null;
			return b;
		});
		assertThat(ClosureRequirements.missing(input)).containsExactly("RESULT", "CAUSE");
	}

	@Test
	void theCauseNeedsItsDescriptionUnlessItIsUndetermined() {
		for (CauseKind kind : List.of(CauseKind.CONFIRMED, CauseKind.PROBABLE)) {
			Input input = with(complete(ElementClass.EQUIPMENT), b -> {
				b.causeKind = kind;
				b.causeText = "  ";
				return b;
			});
			assertThat(ClosureRequirements.missing(input)).as(kind.name()).contains("CAUSE_TEXT");
		}
		Input undetermined = with(complete(ElementClass.EQUIPMENT), b -> {
			b.causeKind = CauseKind.UNDETERMINED;
			b.causeText = null;
			return b;
		});
		assertThat(ClosureRequirements.missing(undetermined)).isEmpty();
	}

	@Test
	void aProbableCauseNeedsAtLeastOneEvidenceOfTheEpisode() {
		Input without = with(complete(ElementClass.EQUIPMENT), b -> {
			b.causeKind = CauseKind.PROBABLE;
			b.episodeEvidence = false;
			return b;
		});
		assertThat(ClosureRequirements.missing(without)).containsExactly("PROBABLE_CAUSE_EVIDENCE");

		Input confirmedWithout = with(complete(ElementClass.EQUIPMENT), b -> {
			b.causeKind = CauseKind.CONFIRMED;
			b.episodeEvidence = false;
			return b;
		});
		assertThat(ClosureRequirements.missing(confirmedWithout)).isEmpty();
	}

	@Test
	void anEquipmentNeedsTheFourChecksAndEachOneAnswered() {
		Input none = with(complete(ElementClass.EQUIPMENT), b -> {
			b.checks = Map.of();
			return b;
		});
		assertThat(ClosureRequirements.missing(none)).containsExactly("CLOSURE_CHECKS");

		Input three = with(complete(ElementClass.EQUIPMENT), b -> {
			b.checks = Map.of("platformNormal", ClosureCheck.CONFIRMED, "stabilityChecked", ClosureCheck.CONFIRMED,
					"noAffectedUser", ClosureCheck.CONFIRMED);
			return b;
		});
		assertThat(ClosureRequirements.missing(three)).containsExactly("CLOSURE_CHECKS");
	}

	@Test
	void theChatbotAndASiteElementDoNotNeedTheChargerChecks() {
		for (ElementClass elementClass : List.of(ElementClass.CHATBOT_SERVICE, ElementClass.SITE_ELEMENT)) {
			Input input = with(complete(elementClass), b -> {
				b.checks = Map.of();
				return b;
			});
			assertThat(ClosureRequirements.missing(input)).as(elementClass.name()).isEmpty();
		}
	}

	@Test
	void theFinalEvidenceAndTheClosureCommunicationCanBeWaivedWithAJustification() {
		Input missing = with(complete(ElementClass.EQUIPMENT), b -> {
			b.finalEvidence = false;
			b.communication = false;
			return b;
		});
		assertThat(ClosureRequirements.missing(missing)).containsExactly("FINAL_EVIDENCE", "CLOSURE_COMMUNICATION");

		Input waived = with(missing, b -> {
			b.finalWaiver = "No hubo captura";
			b.communicationWaiver = "Se avisó por llamada";
			return b;
		});
		assertThat(ClosureRequirements.missing(waived)).isEmpty();

		Input blank = with(missing, b -> {
			b.finalWaiver = "   ";
			b.communicationWaiver = "";
			return b;
		});
		assertThat(ClosureRequirements.missing(blank)).containsExactly("FINAL_EVIDENCE", "CLOSURE_COMMUNICATION");
	}

	@Test
	void aDeviationFromTheReferenceNeedsItsJustification() {
		Input outside = with(complete(ElementClass.EQUIPMENT), b -> {
			b.outside = true;
			return b;
		});
		assertThat(ClosureRequirements.missing(outside)).containsExactly("DEVIATION_JUSTIFICATION");

		Input justified = with(outside, b -> {
			b.deviation = "Esperó al proveedor";
			return b;
		});
		assertThat(ClosureRequirements.missing(justified)).isEmpty();
	}

	@Test
	void aDuplicateNeedsItsOriginalTicket() {
		Input input = with(complete(ElementClass.EQUIPMENT), b -> {
			b.result = ClosureResult.DUPLICATE;
			return b;
		});
		assertThat(ClosureRequirements.missing(input)).containsExactly("DUPLICATE_OF");

		Input withOriginal = with(input, b -> {
			b.duplicateOf = UUID.randomUUID();
			return b;
		});
		assertThat(ClosureRequirements.missing(withOriginal)).isEmpty();
	}

	@Test
	void aTicketClosedAsDuplicateOrRegisteredInErrorOnlyNeedsT4TheResultAndTheCause() {
		for (ClosureResult result : List.of(ClosureResult.DUPLICATE, ClosureResult.REGISTERED_IN_ERROR)) {
			Input bare = new Input(ElementClass.EQUIPMENT, T4, statuses(MilestoneStatus.PENDING, MilestoneStatus.PENDING,
					MilestoneStatus.PENDING), result, UUID.randomUUID(), CauseKind.UNDETERMINED, null, Map.of(), false, null,
					false, false, null, false, null);
			assertThat(ClosureRequirements.missing(bare)).as(result.name()).isEmpty();
		}
		Input noCause = new Input(ElementClass.EQUIPMENT, null, Map.of(), ClosureResult.REGISTERED_IN_ERROR, null, null,
				null, Map.of(), false, null, false, false, null, false, null);
		assertThat(ClosureRequirements.missing(noCause)).containsExactly("T4", "CAUSE");
	}

	@Test
	void everythingMissingIsReportedAtOnceInTheContractOrder() {
		Input nothing = new Input(ElementClass.EQUIPMENT, null, Map.of(), null, null, null, null, Map.of(), false, null,
				false, false, null, true, null);
		assertThat(ClosureRequirements.missing(nothing)).containsExactly("T4", "T1", "T2", "T3", "RESULT", "CAUSE",
				"CLOSURE_CHECKS", "FINAL_EVIDENCE", "CLOSURE_COMMUNICATION", "DEVIATION_JUSTIFICATION");
	}
}
