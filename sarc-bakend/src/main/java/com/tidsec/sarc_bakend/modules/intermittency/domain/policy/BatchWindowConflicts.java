package com.tidsec.sarc_bakend.modules.intermittency.domain.policy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing.IssueCode;

/**
 * Duplicados y solapamientos entre ventanas del mismo punto que vienen de filas distintas del mismo lote (los de
 * una misma fila los detecta el {@code ImportRowInterpreter}). Una ventana abierta ocupa
 * {@code [inicio, infinito)}, asi que las posteriores de otras filas del punto quedan en conflicto con ella
 * (FR-014). Es puro: el clasificador le entrega solo las ventanas que cuentan.
 */
public final class BatchWindowConflicts {

	private BatchWindowConflicts() {
	}

	/** Una ventana candidata de una fila; {@code end} nulo es una ventana abierta. */
	public record Candidate(UUID rowId, int pair, Instant start, Instant end) {

		public String key() {
			return BatchWindowConflicts.key(rowId, pair);
		}

		static boolean overlaps(Candidate left, Candidate right) {
			Instant leftEnd = left.end == null ? Instant.MAX : left.end;
			Instant rightEnd = right.end == null ? Instant.MAX : right.end;
			return left.start.isBefore(rightEnd) && right.start.isBefore(leftEnd);
		}

		static boolean identical(Candidate left, Candidate right) {
			return left.start.equals(right.start) && Objects.equals(left.end, right.end);
		}
	}

	public static String key(UUID rowId, int pair) {
		return rowId + ":" + pair;
	}

	/**
	 * Devuelve, por clave de ventana, el motivo de su conflicto con otra fila: {@code DUPLICATE_IN_BATCH} si es
	 * identica a una de ellas, {@code OVERLAP_IN_BATCH} si solo se solapa. Las ventanas libres no aparecen.
	 */
	public static Map<String, IssueCode> detect(List<Candidate> candidates) {
		Map<String, IssueCode> conflicts = new HashMap<>();
		for (int i = 0; i < candidates.size(); i++) {
			Candidate left = candidates.get(i);
			for (int j = i + 1; j < candidates.size(); j++) {
				Candidate right = candidates.get(j);
				if (left.rowId().equals(right.rowId()) || !Candidate.overlaps(left, right)) {
					continue;
				}
				IssueCode code = Candidate.identical(left, right) ? IssueCode.DUPLICATE_IN_BATCH
						: IssueCode.OVERLAP_IN_BATCH;
				conflicts.merge(left.key(), code, BatchWindowConflicts::stronger);
				conflicts.merge(right.key(), code, BatchWindowConflicts::stronger);
			}
		}
		return conflicts;
	}

	/** Un duplicado explica mas que un solapamiento. */
	private static IssueCode stronger(IssueCode current, IssueCode other) {
		return current == IssueCode.DUPLICATE_IN_BATCH || other == IssueCode.DUPLICATE_IN_BATCH
				? IssueCode.DUPLICATE_IN_BATCH : IssueCode.OVERLAP_IN_BATCH;
	}
}
