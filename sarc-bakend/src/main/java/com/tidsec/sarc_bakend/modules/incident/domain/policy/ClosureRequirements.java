package com.tidsec.sarc_bakend.modules.incident.domain.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.CauseKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureCheck;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;

/**
 * Requisitos para cerrar un ticket (FR-047, FR-048, FR-048a). Es una funcion pura que devuelve, en orden, cada requisito
 * que falta con el codigo estable del contrato ({@code TICKET_CLOSE_REQUIREMENTS}); vacia significa que se puede cerrar.
 *
 * <p>Un ticket de <b>equipo</b> exige T4, T1 a T3 en un estado explicito, el resultado, la causa, las cuatro
 * confirmaciones del protocolo, una evidencia final o su justificacion, la comunicacion de cierre o su justificacion y,
 * si quedo fuera de la referencia, la justificacion de la desviacion. El <b>chatbot</b> y el <b>elemento del sitio</b>
 * piden lo mismo salvo las confirmaciones, que son de cargadores fisicos.
 *
 * <p>Cerrar como «Duplicado» o «Registrado por error» es la forma de corregir un ticket que no debio existir (FR-036,
 * FR-049): solo exige T4, el resultado, el ticket original de un duplicado y la causa. Esos tickets quedan fuera de los
 * indicadores (FR-050), asi que no se les pide completar el seguimiento de un caso que no fue real.
 */
public final class ClosureRequirements {

	public static final String T4 = "T4";
	public static final String T1 = "T1";
	public static final String T2 = "T2";
	public static final String T3 = "T3";
	public static final String RESULT = "RESULT";
	public static final String DUPLICATE_OF = "DUPLICATE_OF";
	public static final String CAUSE = "CAUSE";
	public static final String CAUSE_TEXT = "CAUSE_TEXT";
	public static final String PROBABLE_CAUSE_EVIDENCE = "PROBABLE_CAUSE_EVIDENCE";
	public static final String CLOSURE_CHECKS = "CLOSURE_CHECKS";
	public static final String FINAL_EVIDENCE = "FINAL_EVIDENCE";
	public static final String CLOSURE_COMMUNICATION = "CLOSURE_COMMUNICATION";
	public static final String DEVIATION_JUSTIFICATION = "DEVIATION_JUSTIFICATION";

	/** Las cuatro confirmaciones del protocolo que aplican a un equipo, en el orden en que se muestran. */
	public static final List<String> CHECK_KEYS = List.of("platformNormal", "stabilityChecked", "noAffectedUser",
			"postTest");

	private ClosureRequirements() {
	}

	/**
	 * @param t4 la hora de cierre propuesta; nula si falta
	 * @param statuses el estado de T1 a T3 del episodio
	 * @param checks las confirmaciones por clave; solo se leen en un equipo
	 * @param hasFinalEvidence hay una evidencia final no anulada en el episodio
	 * @param hasEpisodeEvidence hay alguna evidencia no anulada en el episodio (respalda una causa probable)
	 * @param hasClosureCommunication hay una comunicacion de cierre no anulada en el episodio
	 * @param outsideReference algun juicio del episodio quedo fuera de la referencia
	 */
	public record Input(ElementClass elementClass, Instant t4, Map<MilestoneKind, MilestoneStatus> statuses,
			ClosureResult result, UUID duplicateOfTicketId, CauseKind causeKind, String causeText,
			Map<String, ClosureCheck> checks, boolean hasFinalEvidence, String finalEvidenceWaiver,
			boolean hasEpisodeEvidence, boolean hasClosureCommunication, String closureCommunicationWaiver,
			boolean outsideReference, String deviationJustification) {

		public Input {
			statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
			checks = checks == null ? Map.of() : Map.copyOf(checks);
		}
	}

	public static List<String> missing(Input input) {
		List<String> missing = new ArrayList<>();
		if (input.t4() == null) {
			missing.add(T4);
		}
		boolean excluded = input.result() != null && input.result().excludedFromIndicators();
		if (!excluded) {
			for (MilestoneKind kind : new MilestoneKind[] { MilestoneKind.T1, MilestoneKind.T2, MilestoneKind.T3 }) {
				if (input.statuses().getOrDefault(kind, MilestoneStatus.PENDING) == MilestoneStatus.PENDING) {
					missing.add(kind.name());
				}
			}
		}
		if (input.result() == null) {
			missing.add(RESULT);
		}
		else if (input.result() == ClosureResult.DUPLICATE && input.duplicateOfTicketId() == null) {
			missing.add(DUPLICATE_OF);
		}
		if (input.causeKind() == null) {
			missing.add(CAUSE);
		}
		else {
			if (input.causeKind().needsDescription() && isBlank(input.causeText())) {
				missing.add(CAUSE_TEXT);
			}
			if (input.causeKind().needsEvidence() && !input.hasEpisodeEvidence()) {
				missing.add(PROBABLE_CAUSE_EVIDENCE);
			}
		}
		if (excluded) {
			return missing;
		}
		if (input.elementClass() == ElementClass.EQUIPMENT && !confirmed(input.checks())) {
			missing.add(CLOSURE_CHECKS);
		}
		if (!input.hasFinalEvidence() && isBlank(input.finalEvidenceWaiver())) {
			missing.add(FINAL_EVIDENCE);
		}
		if (!input.hasClosureCommunication() && isBlank(input.closureCommunicationWaiver())) {
			missing.add(CLOSURE_COMMUNICATION);
		}
		if (input.outsideReference() && isBlank(input.deviationJustification())) {
			missing.add(DEVIATION_JUSTIFICATION);
		}
		return missing;
	}

	/** Cada una de las cuatro confirmaciones esta respondida: «Confirmada» o «No aplica». */
	private static boolean confirmed(Map<String, ClosureCheck> checks) {
		return CHECK_KEYS.stream().allMatch(checks::containsKey);
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}
}
