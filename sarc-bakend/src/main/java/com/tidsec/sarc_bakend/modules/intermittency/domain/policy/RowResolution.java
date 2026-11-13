package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowClassification;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowDecision;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowInterpretation;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.RowWindow;
import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.WindowResolution;

/**
 * Cuando una fila ya esta resuelta para confirmar el lote (FR-028): no queda nada por decidir. Una fila se
 * resuelve sola si es NEW, IDENTICAL o solo tiene una ventana larga por revisar; si no, necesita una decision.
 */
public final class RowResolution {

	private RowResolution() {
	}

	public static boolean isResolved(RowDecision decision, RowClassification classification,
			RowInterpretation interpretation) {
		if (decision == RowDecision.SKIP) {
			return true;
		}
		return switch (classification) {
			case NEW, IDENTICAL -> true;
			// El total del archivo debe acordarse o corregirse; una ventana larga queda por revisar en el sistema.
			case REVIEW -> !interpretation.rowIssues().contains(IssueCode.TOTAL_MISMATCH)
					|| interpretation.totalAcknowledged();
			// Solo un solapamiento con lo guardado se resuelve en la fila: reemplazando la ventana existente.
			case CONFLICT -> decision == RowDecision.KEEP_PENDING || conflictsAreReplaced(interpretation);
			case PENDING_LINK -> decision == RowDecision.KEEP_PENDING;
			case OUT_OF_SCOPE, INVALID -> false;
		};
	}

	/** Cada ventana en conflicto se solapa con una sola ventana guardada y se decidio reemplazarla. */
	private static boolean conflictsAreReplaced(RowInterpretation interpretation) {
		for (RowWindow window : interpretation.active()) {
			if (window.classification() != RowClassification.CONFLICT) {
				continue;
			}
			boolean onlyStored = window.issues().stream().filter(code -> code.classification()
					== RowClassification.CONFLICT).allMatch(code -> code == IssueCode.OVERLAPS_EXISTING);
			if (!onlyStored || window.decision() != WindowResolution.REPLACE || window.conflictWindowId() == null) {
				return false;
			}
		}
		return true;
	}

	/** La fila se aplica al confirmar: no se omitio ni queda pendiente. */
	public static boolean appliesOnConfirm(RowDecision decision) {
		return decision == null || decision == RowDecision.APPLY;
	}
}
