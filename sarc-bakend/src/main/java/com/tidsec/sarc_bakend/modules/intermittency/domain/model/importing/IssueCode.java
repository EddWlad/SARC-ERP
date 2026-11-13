package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

/** Motivo que acompana a una fila o a una ventana interpretada (data-model.md). */
public enum IssueCode {
	// Error de dato.
	DATE_FORMAT(RowClassification.INVALID),
	TIME_FORMAT(RowClassification.INVALID),
	SECONDS_AMBIGUOUS(RowClassification.INVALID),
	END_BEFORE_START(RowClassification.INVALID),
	ZERO_DURATION(RowClassification.INVALID),
	INCOMPLETE_PAIR(RowClassification.INVALID),
	FUTURE_INSTANT(RowClassification.INVALID),
	// Comparacion con lo guardado y con el lote.
	ALREADY_RECORDED(RowClassification.IDENTICAL),
	OVERLAPS_EXISTING(RowClassification.CONFLICT),
	OVERLAP_IN_BATCH(RowClassification.CONFLICT),
	DUPLICATE_IN_BATCH(RowClassification.CONFLICT),
	// Punto desconocido.
	UNKNOWN_CODE(RowClassification.PENDING_LINK),
	AMBIGUOUS_CODE(RowClassification.PENDING_LINK),
	DELETED_EQUIPMENT(RowClassification.PENDING_LINK),
	MISSING_CODE(RowClassification.PENDING_LINK),
	// Fuera de alcance.
	NOT_KIA_CHARGING_POINT(RowClassification.OUT_OF_SCOPE),
	// Observaciones que piden revision.
	TOTAL_MISMATCH(RowClassification.REVIEW),
	LONG_WINDOW(RowClassification.REVIEW),
	// Informativas: no bloquean.
	NAME_DIFFERS(RowClassification.NEW),
	INACTIVE_EQUIPMENT(RowClassification.NEW),
	TOTAL_MISSING(RowClassification.NEW);

	private final RowClassification classification;

	IssueCode(RowClassification classification) {
		this.classification = classification;
	}

	public RowClassification classification() {
		return classification;
	}
}
