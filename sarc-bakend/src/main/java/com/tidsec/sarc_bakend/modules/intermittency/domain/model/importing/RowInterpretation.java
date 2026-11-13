package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo que se entendio de una fila: fecha, codigo, ventanas y comparacion del total. Se recalcula despues de cada
 * edicion o decision a partir de los valores originales y las ediciones; nunca se corrige en silencio (FR-024).
 *
 * <p>{@code computedTotalMinutes} suma las ventanas validas que no estan ignoradas.
 * {@code computedWithDuplicatesMinutes} solo existe mientras haya ventanas duplicadas sin resolver: es la suma
 * contando todas, para mostrar el total con y sin el duplicado (FR-027). {@code rowIssues} son los motivos de la
 * fila entera, como la fecha invalida o el punto desconocido.
 */
public record RowInterpretation(String date, String code, String name, String comment, List<RowWindow> windows,
		List<IssueCode> rowIssues, Integer fileTotalMinutes, Integer computedTotalMinutes,
		Integer computedWithDuplicatesMinutes, boolean totalAcknowledged) {

	public RowInterpretation {
		windows = List.copyOf(windows);
		rowIssues = List.copyOf(rowIssues);
	}

	public LocalDate day() {
		return date == null ? null : LocalDate.parse(date);
	}

	/** Las ventanas que cuentan: no ignoradas. */
	public List<RowWindow> active() {
		return windows.stream().filter(window -> !window.ignored()).toList();
	}

	/** La peor clasificacion entre las ventanas activas y los motivos de la fila. */
	public RowClassification classification() {
		List<RowClassification> all = new ArrayList<>(rowIssues.stream().map(IssueCode::classification).toList());
		active().forEach(window -> all.add(window.classification()));
		RowClassification worst = RowClassification.worst(all);
		// Un total acordado por la persona ya no pide revision; la ventana larga si sigue pidiendola.
		if (worst == RowClassification.REVIEW && totalAcknowledged && !hasLongWindow()
				&& rowIssues.contains(IssueCode.TOTAL_MISMATCH)) {
			return RowClassification.NEW;
		}
		return worst;
	}

	public boolean hasLongWindow() {
		return active().stream().anyMatch(window -> window.has(IssueCode.LONG_WINDOW));
	}

	public RowInterpretation withWindows(List<RowWindow> newWindows) {
		return new RowInterpretation(date, code, name, comment, newWindows, rowIssues, fileTotalMinutes,
				computedTotalMinutes, computedWithDuplicatesMinutes, totalAcknowledged);
	}

	public RowInterpretation withRowIssues(List<IssueCode> newIssues) {
		return new RowInterpretation(date, code, name, comment, windows, newIssues, fileTotalMinutes,
				computedTotalMinutes, computedWithDuplicatesMinutes, totalAcknowledged);
	}

	public RowInterpretation withTotals(Integer computed, Integer withDuplicates, List<IssueCode> newIssues) {
		return new RowInterpretation(date, code, name, comment, windows, newIssues, fileTotalMinutes, computed,
				withDuplicates, totalAcknowledged);
	}

	public RowInterpretation withTotalAcknowledged(boolean acknowledged) {
		return new RowInterpretation(date, code, name, comment, windows, rowIssues, fileTotalMinutes,
				computedTotalMinutes, computedWithDuplicatesMinutes, acknowledged);
	}
}
