package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Una ventana interpretada de una fila: el par de Inicio y Fin, con sus instantes, su clasificacion y su
 * decision. Los instantes se guardan como texto ISO-8601 con desfase para que el JSON no dependa del
 * serializador. {@code startAt} es nulo si el inicio no se pudo interpretar y {@code endAt} si falta o esta
 * invalido; una ventana con decision {@link WindowResolution#OPEN} no tiene fin.
 *
 * <p>{@code conflictWindowId} es la ventana ya guardada con la que se solapa, cuando es una sola.
 * {@code mergedIntoPair} indica que esta ventana se fusiono en otra de la misma fila.
 */
public record RowWindow(int pair, String startText, String endText, String startAt, String endAt, Integer minutes,
		boolean endsNextDay, boolean test, boolean added, WindowResolution decision, String conflictWindowId,
		Integer mergedIntoPair, List<IssueCode> issues, RowClassification classification) {

	public RowWindow {
		issues = List.copyOf(issues);
	}

	public Instant startInstant() {
		return startAt == null ? null : OffsetDateTime.parse(startAt).toInstant();
	}

	public Instant endInstant() {
		return endAt == null ? null : OffsetDateTime.parse(endAt).toInstant();
	}

	/**
	 * Ignorada: la persona la omitio, conservo la ya registrada o se fusiono en otra. No se crea ni cuenta en los
	 * totales.
	 */
	public boolean ignored() {
		return decision == WindowResolution.OMIT || decision == WindowResolution.KEEP_EXISTING
				|| mergedIntoPair != null;
	}

	/** Una ventana que se puede crear: tiene inicio y fin interpretados, o es abierta con inicio. */
	public boolean hasShape() {
		return startAt != null && (endAt != null || decision == WindowResolution.OPEN);
	}

	public boolean has(IssueCode code) {
		return issues.contains(code);
	}

	public RowWindow withDecision(WindowResolution newDecision) {
		return new RowWindow(pair, startText, endText, startAt, endAt, minutes, endsNextDay, test, added,
				newDecision, conflictWindowId, mergedIntoPair, issues, classification);
	}

	public RowWindow withIssues(List<IssueCode> newIssues, String newConflictWindowId) {
		RowClassification worst = RowClassification.worst(newIssues.stream().map(IssueCode::classification).toList());
		return new RowWindow(pair, startText, endText, startAt, endAt, minutes, endsNextDay, test, added, decision,
				newConflictWindowId, mergedIntoPair, newIssues, worst);
	}

	public RowWindow withTimes(String newStartAt, String newEndAt, Integer newMinutes, Integer newMergedInto) {
		return new RowWindow(pair, startText, endText, newStartAt, newEndAt, newMinutes, endsNextDay, test, added,
				decision, conflictWindowId, newMergedInto, issues, classification);
	}
}
